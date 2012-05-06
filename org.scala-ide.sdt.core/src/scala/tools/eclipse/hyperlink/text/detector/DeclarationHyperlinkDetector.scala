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

class DeclarationHyperlinkDetector extends BaseHyperlinkDetector {

  private val resolver: ScalaDeclarationHyperlinkComputer = new ScalaDeclarationHyperlinkComputer

  override protected[detector] def runDetectionStrategy(scu: ScalaCompilationUnit, textEditor: ITextEditor, currentSelection: IRegion): List[IHyperlink] = {
    val wordRegion = ScalaWordFinder.findWord(scu.getContents, currentSelection.getOffset)

    resolver.findHyperlinks(scu, wordRegion) match {
      case None => List()
      case Some(List()) => javaDeclarationHyperlinkComputer(textEditor, wordRegion, scu)
      case Some(hyperlinks) => hyperlinks
    }
  }

  private def javaDeclarationHyperlinkComputer(textEditor: ITextEditor, wordRegion: IRegion, scu: ScalaCompilationUnit): List[IHyperlink] = {
    try {
      val environment = scu.newSearchableEnvironment()
      val requestor = new ScalaSelectionRequestor(environment.nameLookup, scu)
      val engine = new ScalaSelectionEngine(environment, requestor, scu.getJavaProject.getOptions(true))
      val offset = wordRegion.getOffset
      engine.select(scu, offset, offset + wordRegion.getLength - 1)
      val elements = requestor.getElements.toList

      lazy val qualify = elements.length > 1
      lazy val openAction = new OpenAction(textEditor.asInstanceOf[JavaEditor])
      elements.map(new JavaElementHyperlink(wordRegion, openAction, _, qualify))
    } catch {
      case _ => List[IHyperlink]()
    }
  }
}

object DeclarationHyperlinkDetector {
  def apply(): BaseHyperlinkDetector = new DeclarationHyperlinkDetector
}