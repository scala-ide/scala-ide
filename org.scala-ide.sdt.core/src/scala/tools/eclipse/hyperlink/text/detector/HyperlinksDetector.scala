package scala.tools.eclipse.hyperlink.text.detector

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector
import org.eclipse.jface.text.hyperlink.IHyperlink
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.ui.texteditor.ITextEditor

private class HyperlinksDetector extends BaseHyperlinkDetector {

  private val strategies: List[BaseHyperlinkDetector] = List(DeclarationHyperlinkDetector(), ImplicitHyperlinkDetector())

  override protected[detector] def runDetectionStrategy(scu: ScalaCompilationUnit, textEditor: ITextEditor, currentSelection: IRegion): List[IHyperlink] =
    strategies flatMap { _.runDetectionStrategy(scu, textEditor, currentSelection) }
}

object HyperlinksDetector {
  def apply(): AbstractHyperlinkDetector = new HyperlinksDetector
}