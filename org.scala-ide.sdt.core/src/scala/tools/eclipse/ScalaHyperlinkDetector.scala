/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$
package scala.tools.eclipse

import org.eclipse.jdt.core.{ ICodeAssist, IJavaElement }
import org.eclipse.jface.text.{ IRegion, ITextViewer }
import org.eclipse.jface.text.hyperlink.{ AbstractHyperlinkDetector, IHyperlink }
import org.eclipse.jdt.internal.core.Openable
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jdt.internal.ui.javaeditor.{ EditorUtility, JavaElementHyperlink }
import org.eclipse.jdt.ui.actions.OpenAction
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import javaelements.{ ScalaCompilationUnit, ScalaSelectionEngine, ScalaSelectionRequestor }
import scala.tools.eclipse.logging.HasLogger

class ScalaHyperlinkDetector extends AbstractHyperlinkDetector with HasLogger {
  def detectHyperlinks(viewer: ITextViewer, region: IRegion, canShowMultipleHyperlinks: Boolean): Array[IHyperlink] = {
    val textEditor = getAdapter(classOf[ITextEditor]).asInstanceOf[ITextEditor]
    detectHyperlinks(textEditor, region, canShowMultipleHyperlinks)
  }

  def scalaHyperlinks(scu: ScalaCompilationUnit, wordRegion: IRegion): Option[List[IHyperlink]] = {
    scu.withSourceFile({ (sourceFile, compiler) =>
      if (wordRegion == null || wordRegion.getLength == 0)
        None
      else {
        val start = wordRegion.getOffset
        val regionEnd = wordRegion.getOffset + wordRegion.getLength
        // removing 1 handles correctly hyperlinking requests @ EOF
        val end = if(sourceFile.length == regionEnd) regionEnd - 1 else regionEnd
          
        val pos = compiler.rangePos(sourceFile, start, start, end)

        import compiler.{ log => _, _ }
        val response = new Response[compiler.Tree]
        askTypeAt(pos, response)
        val typed = response.get

        logger.info("detectHyperlinks: wordRegion = " + wordRegion)
        compiler.askOption { () =>
          typed.left.toOption map {
            case Import(expr, sels) => 
              if(expr.pos.includes(pos)) {
                @annotation.tailrec
                def locate(p: Position, inExpr: Tree): Symbol = inExpr match {
                  case Select(qualifier, name) =>
                    if(qualifier.pos.includes(p)) locate(p, qualifier)
                    else inExpr.symbol
                  case tree => tree.symbol
                }
                
                List(locate(pos, expr))
              }
              else {
                sels find (selPos => selPos.namePos >= pos.start && selPos.namePos <= pos.end) map { sel =>
                  val tpe = stabilizedType(expr)
                  List(tpe.member(sel.name), tpe.member(sel.name.toTypeName))
                } getOrElse Nil
              }
            case Annotated(atp, _) => List(atp.symbol)
            case st: SymTree       => List(st.symbol)
            case t                 => logger.info("unhandled tree " + t.getClass); List()
          } flatMap { list =>
            val filteredSyms = list filterNot { sym => sym.isPackage || sym == NoSymbol }
            if (filteredSyms.isEmpty) None else Some(
              filteredSyms.foldLeft(List[IHyperlink]()) { (links, sym) =>
                if (sym.isJavaDefined) links
                else {
                  object DeclarationHyperlinkFactory extends scala.tools.eclipse.hyperlink.DeclarationHyperlinkFactory {
                    protected val global: compiler.type = compiler
                  }
                  DeclarationHyperlinkFactory.create(scu, sym, wordRegion) match {
                    case None => links
                    case Some(l) => l :: links
                  }
                }
              })
          }
        }.flatten.headOption match {
          case links @ Some(List()) =>
            logger.info("Falling back to selection engine for %s!".format(typed.left))
            links
          case links =>
            links 
        }
      }
    })(None)
  }

  def detectHyperlinks(textEditor: ITextEditor, currentSelection: IRegion, canShowMultipleHyperlinks: Boolean): Array[IHyperlink] = {
    if (textEditor == null) // can be null if generated through ScalaPreviewerFactory
      null
    else
      EditorUtility.getEditorInputJavaElement(textEditor, false) match {
        case scu: ScalaCompilationUnit =>
          import scala.tools.eclipse.semantichighlighting.implicits.ImplicitConversionAnnotation
          import scala.tools.eclipse.ui.EditorUtils.{withEditor, getAnnotationsAtOffset}
          import scala.collection.mutable.ListBuffer
          
          var links = ListBuffer[IHyperlink]()
          
          withEditor(scu) { editor =>
            for ((ann, pos) <- getAnnotationsAtOffset(editor, currentSelection.getOffset)) ann match {
              case a: ImplicitConversionAnnotation if a.sourceLink.isDefined => 
                a.sourceLink.get +: links
              case _ => ()
            }  
          }

          val wordRegion = ScalaWordFinder.findWord(scu.getContents, currentSelection.getOffset)

          scalaHyperlinks(scu, wordRegion) match {
            case None             => null // do not try to use codeSelect.
            case Some(List())    => 
              codeSelect(textEditor, wordRegion, scu)
            case Some(hyperlinks) =>    
             (hyperlinks ::: links.toList).toArray 
          }

        case _ => null
      }
  }

  //Default path used for selecting.
  def codeSelect(textEditor: ITextEditor, wordRegion: IRegion, scu: ScalaCompilationUnit): Array[IHyperlink] = {
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