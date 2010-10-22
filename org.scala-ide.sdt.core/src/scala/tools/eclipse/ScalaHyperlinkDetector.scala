/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$
package scala.tools.eclipse

import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.{ICodeAssist, IJavaElement}
import org.eclipse.jface.text.{IRegion, ITextViewer}
import org.eclipse.jface.text.hyperlink.{AbstractHyperlinkDetector, IHyperlink}
import org.eclipse.jdt.internal.core.{ClassFile,Openable}
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
import org.eclipse.jdt.ui.actions.SelectionDispatchAction
import org.eclipse.jdt.internal.ui.javaeditor.{EditorUtility, JavaElementHyperlink}

import scala.reflect.generic.Flags._
import scala.tools.nsc.util.BatchSourceFile
import scala.tools.nsc.io.AbstractFile
import scala.tools.eclipse.refactoring.EditorHelpers

import javaelements.{ScalaSourceFile, ScalaClassFile}
import util.EclipseFile

class ScalaHyperlinkDetector extends AbstractHyperlinkDetector {
  def detectHyperlinks(viewer : ITextViewer, region : IRegion, canShowMultipleHyperlinks : Boolean) : Array[IHyperlink] = {
    EditorHelpers.withCurrentScalaSourceFile { ssf =>
      ssf.withSourceFile { (sourceFile, compiler) =>
	    val wordRegion = ScalaWordFinder.findWord(viewer.getDocument, region.getOffset)
	    if (wordRegion == null || wordRegion.getLength == 0) null else  {
          val pos = compiler.rangePos(sourceFile, wordRegion.getOffset, wordRegion.getOffset, wordRegion.getOffset + wordRegion.getLength)
  
          val response = new compiler.Response[compiler.Tree]
          compiler.askTypeAt(pos, response)
          val typed = response.get
        
          println("detectHyperlinks: wordRegion = "+wordRegion)
      
          compiler.ask { () =>
            case class Hyperlink(file : Openable, pos : Int) extends IHyperlink {
              def getHyperlinkRegion = wordRegion
              def getTypeLabel = null
              def getHyperlinkText = "Open Declaration"
              def open = {
                EditorUtility.openInEditor(file, true) match { 
                  case editor : ITextEditor => editor.selectAndReveal(pos, 0)
                  case _ =>
                }
              }
            }
            import compiler._
            typed.left.toOption map { tree : Tree => tree match {
              case i : Ident => i.symbol 
              case s : Select => s.symbol
	          case Annotated(atp, _) => atp.symbol
              // TODO: imports
              case _ => NoSymbol
            }
          } flatMap { sym => 
            if (sym.isPackage || sym == NoSymbol || sym.isJavaDefined) None else {
	     	  def find[T, V](arr : Array[T])(f : T => Option[V]) : Option[V] = {
	            for(e <- arr) {
		          f(e) match {
		            case v@Some(_) => return v
	                case None =>
		          }
		        }
		        None
	      	  }
	     	  def findClassFile = {
	     	 	val packName = sym.enclosingPackage.fullName
	            val pfs = ssf.newSearchableEnvironment.nameLookup.findPackageFragments(packName, false)
	     	    find(pfs) {
	              val top = sym.toplevelClass
	              val name = top.name + (if (top.isModule && !top.isJavaDefined) "$" else "") + ".class"
		          _.getClassFile(name) match {
		            case classFile : ScalaClassFile if classFile.exists => Some(classFile)
		            case _ => None
		          }
	     	    }
	     	  }
              (if (sym.sourceFile ne null) {
                val path = new Path(sym.sourceFile.path)
                val root = ResourcesPlugin.getWorkspace().getRoot()
                root.findFilesForLocation(path) match {
         	      case arr : Array[_] if arr.length == 1 =>
                    ScalaSourceFile.createFromPath(arr(0).getFullPath.toString)
         	      case _ => findClassFile
                }
              } else findClassFile) flatMap { file =>
                if (sym.pos eq NoPosition) {
                  val traverser = new Traverser {
                    def equiv(src : DefTree, clz : Symbol) = {
	    	          src match {
	    	           //fixme: ugly toString compare + =:= doesn't work. 
	    	           case DefDef(_, name, _, paramss, _, _) if clz.isMethod => 
	    	              name.toString == clz.decodedName.toString && paramss.corresponds(clz.paramss) {
	    	                case (sec1, sec2) => sec1.corresponds(sec2) {case (p1, p2) => p1.tpe =:= p2.tpe}
                          }
                        case TypeDef(_,name,_,_) if clz.isType => name.toString == clz.decodedName.toString
                        case ClassDef(_,name,_,_) if clz.isClass || clz.isTrait => name.toString == clz.decodedName.toString
                        case ValDef(_,name,_,_) if clz.isValue => name.toString == clz.decodedName.toString
                        case ModuleDef(_,name,_) if clz.isModule => name.toString == clz.decodedName.toString
	  	                case _ => false
	    	          }
	    	        }
		    	  
	    	        var owners = sym.ownerChain.reverse.tail   // drop root symbol
	    	        def packageNames(ref : RefTree) = {
	    	          def inner(ref : RefTree, acc : List[Name]) : List[Name] = ref match {
	    	     	    case Select(q : RefTree, name) => inner(q, name::acc)
	    	     	    case _ => ref.name::acc
	    	          }
	    	          inner(ref, Nil)
	    	        }
	    	    	    	  
	    	        override def traverse(t : Tree) {
                      t match {
                        case _ : Template => super.traverse(t)
                        case PackageDef(ref, stats) => {
                          val names = packageNames(ref)
                          val (pre, rest) = owners.splitAt(names.length)
                          if (pre.corresponds(names) {case (owner, name) => owner.sourceModule.isPackage && owner.name.toString == name.toString}) {
                    	    owners = rest
                    	    super.traverseTrees(stats)  
                          }
                        }
                        case dt : DefTree if equiv(dt, owners.head) => owners.tail match {
                          case Nil => sym.setPos(dt.pos)
	    	 	          case tl =>
                            owners = tl
	    	                super.traverse(t)
	    	            }
	    	            case _ =>
	    	          }
	    	        }
	    	      }
                  file.withSourceFile{ (f, _) =>
                    traverser traverse compiler.body(f)
                  }
                }
                Some(Hyperlink(file, sym.pos.pointOrElse(-1)))
         	  }
            }
	        }
	      } match {
	        case Some(hyper) => Left(Array[IHyperlink](hyper))
	        case None => Right( () => codeSelect(viewer, wordRegion) )
	      }
	    } match {
	   	  case Left(l) => l
	      case Right(cont) => cont()
	    }
	  }
    }.getOrElse(null)
  }
  
  //Default path used for selecting.
  def codeSelect(viewer : ITextViewer, wordRegion : IRegion) : Array[IHyperlink] = {
	val offset = wordRegion.getOffset
    val textEditor = getAdapter(classOf[ITextEditor]).asInstanceOf[ITextEditor]
    textEditor.getAction("OpenEditor") match {
	  case openAction : SelectionDispatchAction =>
		val input = EditorUtility.getEditorInputJavaElement(textEditor, false)
        if (input eq null) null else {
         try {
           val editorInput = textEditor.getEditorInput
           val project = ScalaPlugin.plugin.getScalaProject(editorInput)
           val document = textEditor.getDocumentProvider.getDocument(editorInput)
           def isLinkable(element : IJavaElement) = {
             import IJavaElement._
             element.getElementType match {
               case PACKAGE_DECLARATION | PACKAGE_FRAGMENT | PACKAGE_FRAGMENT_ROOT | JAVA_PROJECT | JAVA_MODEL => false
               case _ => true
             }
           }

           val elements = input.asInstanceOf[ICodeAssist].codeSelect(wordRegion.getOffset, wordRegion.getLength).filter(e => e != null && isLinkable(e))
           if (elements.length == 0) null else {
             val qualify = elements.length > 1
             elements.map(new JavaElementHyperlink(wordRegion, openAction.asInstanceOf[SelectionDispatchAction], _, qualify))
           }
         } catch {
           case _ => null
         }    	  
      }
	  case _ => null
	}
  }
}