/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$
package scala.tools.eclipse

import org.eclipse.jdt.core.{ICodeAssist, IJavaElement}
import org.eclipse.jface.text.{IRegion, ITextViewer}
import org.eclipse.jface.text.hyperlink.{AbstractHyperlinkDetector, IHyperlink}
import org.eclipse.jdt.internal.core.Openable
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jdt.internal.ui.javaeditor.{EditorUtility, JavaElementHyperlink}
import org.eclipse.jdt.ui.actions.OpenAction
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor

import javaelements.{ScalaCompilationUnit, ScalaSelectionEngine, ScalaSelectionRequestor}
import util.Logger

class ScalaHyperlinkDetector extends AbstractHyperlinkDetector with Logger {
  def detectHyperlinks(viewer : ITextViewer, region : IRegion, canShowMultipleHyperlinks : Boolean) : Array[IHyperlink] = {
    val textEditor = getAdapter(classOf[ITextEditor]).asInstanceOf[ITextEditor]
    detectHyperlinks(textEditor, region, canShowMultipleHyperlinks)
  }

  case class Hyperlink(file: Openable, pos: Int, len: Int, text: String)(wordRegion: IRegion)  extends IHyperlink {
    def getHyperlinkRegion = wordRegion
    def getTypeLabel = null
    def getHyperlinkText = text
    def open = {
      EditorUtility.openInEditor(file, true) match {
        case editor: ITextEditor => editor.selectAndReveal(pos, len)
        case _ =>
      }
    }
  }
  
  def detectHyperlinks(textEditor: ITextEditor, region: IRegion, canShowMultipleHyperlinks: Boolean): Array[IHyperlink] = {
    if (textEditor == null) // can be null if generated through ScalaPreviewerFactory
      null
    else
      EditorUtility.getEditorInputJavaElement(textEditor, false) match {
        case scu: ScalaCompilationUnit =>
          scu.withSourceFile({ (sourceFile, compiler) =>
            val wordRegion = ScalaWordFinder.findWord(scu.getContents, region.getOffset)
            if (wordRegion == null || wordRegion.getLength == 0)
              null
            else {
              val pos = compiler.rangePos(sourceFile, wordRegion.getOffset, wordRegion.getOffset, wordRegion.getOffset + wordRegion.getLength)

              import compiler.{ log => _, _ }
              val response = new Response[compiler.Tree]
              askTypeAt(pos, response)
              val typed = response.get

              log("detectHyperlinks: wordRegion = " + wordRegion)
              val hyperlinks: Option[List[IHyperlink]] = compiler.askOption { () =>
                typed.left.toOption map {
                  case Import(expr, sels) => sels find (_.namePos >= pos.start) map {sel => 
                    val tpe = stabilizedType(expr)
                    List(tpe.member(sel.name), tpe.member(sel.name.toTypeName))
                  } getOrElse List()
                  case Annotated(atp, _)  => List(atp.symbol)
                  case st: SymTree        => List(st.symbol)
                  case t                  => log("unhandled tree " + t); List()
                } map { list =>
                  list.foldLeft(List[IHyperlink]()) { (l, sym) => 
                    if (sym.isPackage || sym == NoSymbol || sym.isJavaDefined)
                      l
                    else
                      compiler.locate(sym, scu) match {
                        case Some((f, pos)) => {
                          val text = sym.kindString + " " + sym.fullName
                          (Hyperlink(f, pos, wordRegion.getLength, text)(wordRegion): IHyperlink)::l
                        }
                        case _ => l
                      }
                  }
                }
              }.flatten.headOption

              if (!hyperlinks.isDefined || hyperlinks.get.isEmpty) {
                log("!!! Falling back to selection engine for %s!".format(typed.left), Category.ERROR)
                codeSelect(textEditor, wordRegion, scu)
              } else
                hyperlinks.get.toArray
            }
          })(null)

        case _ => null
      }
  }
  
  //Default path used for selecting.
  def codeSelect(textEditor : ITextEditor, wordRegion : IRegion, scu : ScalaCompilationUnit) : Array[IHyperlink] = {
    try {
      val environment = scu.newSearchableEnvironment()
      val requestor = new ScalaSelectionRequestor(environment.nameLookup, scu)
      val engine = new ScalaSelectionEngine(environment, requestor, scu.getJavaProject.getOptions(true))
      val offset = wordRegion.getOffset
      engine.select(scu, offset, offset + wordRegion.getLength - 1)
      val elements = requestor.getElements

      if (elements.length == 0) 
        null 
      else {
        val qualify = elements.length > 1
        val openAction = new OpenAction(textEditor.asInstanceOf[JavaEditor])
        elements.map(new JavaElementHyperlink(wordRegion, openAction, _, qualify))
      }
    } catch {
      case _ => null
    }    	  
  }
}
