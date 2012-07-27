package scala.tools.eclipse.hyperlink.text.detector

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.ui.texteditor.ITextEditor

import scala.tools.eclipse.InteractiveCompilationUnit

private class HyperlinksDetector extends BaseHyperlinkDetector {

  private val strategies: List[BaseHyperlinkDetector] = List(DeclarationHyperlinkDetector(), ImplicitHyperlinkDetector())

  override protected[detector] def runDetectionStrategy(scu: InteractiveCompilationUnit, textEditor: ITextEditor, currentSelection: IRegion): List[IHyperlink] =
    strategies flatMap { _.runDetectionStrategy(scu, textEditor, currentSelection) }
}

object HyperlinksDetector {
  def apply(): AbstractHyperlinkDetector = new HyperlinksDetector
}