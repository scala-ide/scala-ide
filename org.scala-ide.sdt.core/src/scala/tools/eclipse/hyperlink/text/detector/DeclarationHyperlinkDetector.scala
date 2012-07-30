package scala.tools.eclipse.hyperlink.text.detector

import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.eclipse.jdt.internal.ui.javaeditor.JavaElementHyperlink
import org.eclipse.jdt.ui.actions.OpenAction
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.ui.texteditor.ITextEditor
import scala.tools.eclipse.ScalaWordFinder
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSelectionEngine
import scala.tools.eclipse.javaelements.ScalaSelectionRequestor
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.InteractiveCompilationUnit
import org.eclipse.jdt.internal.core.JavaProject
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner

class DeclarationHyperlinkDetector extends BaseHyperlinkDetector with HasLogger {

  private val resolver: ScalaDeclarationHyperlinkComputer = new ScalaDeclarationHyperlinkComputer

  override protected[detector] def runDetectionStrategy(scu: InteractiveCompilationUnit, textEditor: ITextEditor, currentSelection: IRegion): List[IHyperlink] = {
    val input = textEditor.getEditorInput
    val doc = textEditor.getDocumentProvider.getDocument(input)
    val wordRegion = ScalaWordFinder.findWord(doc.get, currentSelection.getOffset)

    resolver.findHyperlinks(scu, wordRegion) match {
      case None => List()
      case Some(List()) =>
        scu match {
          case scalaCU: ScalaCompilationUnit =>
            // the following assumes too heavily a Java compilation unit, being based on the dreaded
            // ScalaSelectionEngine. However, this is a last-resort hyperlinking that uses search for
            // top-level types, and unless there are bugs, normal hyperlinking (through compiler symbols)
            // would find it. So we go here only for `ScalaCompilationUnit`s.
            javaDeclarationHyperlinkComputer(textEditor, wordRegion, scalaCU)
          case _ =>
            Nil
        }
      case Some(hyperlinks) =>
        hyperlinks
    }
  }

  private def javaDeclarationHyperlinkComputer(textEditor: ITextEditor, wordRegion: IRegion, scu: ScalaCompilationUnit): List[IHyperlink] = {
    try {
      val environment = scu.newSearchableEnvironment()
      val requestor = new ScalaSelectionRequestor(environment.nameLookup, scu)
      val engine = new ScalaSelectionEngine(environment, requestor, scu.scalaProject.javaProject.getOptions(true))
      val offset = wordRegion.getOffset
      engine.select(scu, offset, offset + wordRegion.getLength - 1)
      val elements = requestor.getElements.toList

      lazy val qualify = elements.length > 1
      lazy val openAction = new OpenAction(textEditor.asInstanceOf[JavaEditor])
      elements.map(new JavaElementHyperlink(wordRegion, openAction, _, qualify))
    } catch {
      case t: Throwable => 
        logger.debug("Exception while computing hyperlink", t)
        Nil
    }
  }
}

object DeclarationHyperlinkDetector {
  def apply(): BaseHyperlinkDetector = new DeclarationHyperlinkDetector
}