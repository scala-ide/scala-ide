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
import scala.tools.nsc.io.AbstractFile

import javaelements.{ScalaSourceFile, ScalaClassFile, ScalaCompilationUnit}
import util.EclipseFile

class ScalaHyperlinkDetector extends AbstractHyperlinkDetector {
  def detectHyperlinks(viewer : ITextViewer, region : IRegion, canShowMultipleHyperlinks : Boolean) : Array[IHyperlink] = {
    val textEditor = getAdapter(classOf[ITextEditor]).asInstanceOf[ITextEditor]
	Option(EditorUtility.getEditorInputJavaElement(textEditor, false)).flatMap { _ match {
    	case scu : ScalaCompilationUnit => Some(scu)
    	case _ => None
      }
    }.map { scu =>
      scu.withSourceFile { (sourceFile, compiler) =>
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
              case st : SymTree => st.symbol 
	          case Annotated(atp, _) => atp.symbol
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
	            val pfs = scu.newSearchableEnvironment.nameLookup.findPackageFragments(packName, false)
	     	    if (pfs eq null) None else find(pfs) {
	              val top = sym.toplevelClass
	              val name = top.name + (if (top.isModule) "$" else "") + ".class"
		          _.getClassFile(name) match {
		            case classFile : ScalaClassFile => Some(classFile)
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
	    	      object traverser {
	    	     	var owners = sym.ownerChain.reverse

                    def equiv(src : Symbol, clz : Symbol) = {
                      src.decodedName == clz.decodedName && ( 
	    	            if (src.isMethod && clz.isMethod) 
	    	              src.info.toString == clz.info.toString
	    	            else src.hasFlag(PACKAGE) && clz.hasFlag(PACKAGE) ||
    	                     src.isType && clz.isType && !clz.isModuleClass ||
                             src.isTerm && (clz.isTerm || clz.isModuleClass) 
	    	          )
	    	        }
		    	  
	    	        def traverse(srcsym : Symbol) : Boolean = {
	    	          if (equiv(srcsym, owners.head)) owners.tail match {
                        case Nil if srcsym.pos ne NoPosition => sym.setPos(srcsym.pos); true
	    	 	        case tl => {
                          owners = tl
	    	              srcsym.info.decls exists { traverse _ }
	    	 	        }
	    	          } else false
	    	        }
                  }
	    	        
                  file.withSourceFile{ (f, _) =>
                    traverser traverse compiler.root(f).symbol.ownerChain.reverse.head
                    reload(List(f), new Response[Unit])
                    removeUnitOf(f)
                  }
                }
                Some(Hyperlink(file, sym.pos.pointOrElse(-1)))
         	  }
            }
	        }
	      } match {
	        case Some(hyper) => Left(Array[IHyperlink](hyper))
	        case None => Right( () => codeSelect(textEditor, wordRegion, scu) )
	      }
	    } match {
	   	  case Left(l) => l
	      case Right(cont) => cont()
	    }
	  }
    }.getOrElse(null)
  }
  
  //Default path used for selecting.
  def codeSelect(textEditor : ITextEditor, wordRegion : IRegion, scu : ScalaCompilationUnit) : Array[IHyperlink] = {
    textEditor.getAction("OpenEditor") match {
	  case openAction : SelectionDispatchAction =>
        try {
          val editorInput = textEditor.getEditorInput
          def isLinkable(element : IJavaElement) = {
            import IJavaElement._
            element.getElementType match {
              case PACKAGE_DECLARATION | PACKAGE_FRAGMENT | PACKAGE_FRAGMENT_ROOT | JAVA_PROJECT | JAVA_MODEL => false
              case _ => true
            }
          }

          val elements = scu.asInstanceOf[ICodeAssist].codeSelect(wordRegion.getOffset, wordRegion.getLength).filter(e => e != null && isLinkable(e))
          if (elements.length == 0) null else {
            val qualify = elements.length > 1
            elements.map(new JavaElementHyperlink(wordRegion, openAction.asInstanceOf[SelectionDispatchAction], _, qualify))
          }
        } catch {
          case _ => null
        }    	  
	  case _ => null
	}
  }
}