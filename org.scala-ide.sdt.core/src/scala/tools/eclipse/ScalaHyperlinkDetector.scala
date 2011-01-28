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

import javaelements.{ScalaSourceFile, ScalaClassFile, ScalaCompilationUnit, ScalaSelectionEngine, ScalaSelectionRequestor}
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
              compiler.locate(sym, scu) map { case (f, pos) => Hyperlink(f, pos) }
	        }
	      } match {
	        case Some(hyper) => Left(Array[IHyperlink](hyper))
	        case None => Right( () => codeSelect(textEditor, wordRegion, scu) )
	      }
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
          
          val environment = scu.newSearchableEnvironment()
          val requestor = new ScalaSelectionRequestor(environment.nameLookup, scu)
          val engine = new ScalaSelectionEngine(environment, requestor, scu.getJavaProject.getOptions(true))
          val offset = wordRegion.getOffset
          engine.select(scu, offset, offset + wordRegion.getLength - 1)
          val elements = requestor.getElements
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
