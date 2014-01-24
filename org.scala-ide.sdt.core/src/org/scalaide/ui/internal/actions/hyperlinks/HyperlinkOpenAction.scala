package scala.tools.eclipse.ui.actions

import scala.tools.eclipse.hyperlink.text.detector.BaseHyperlinkDetector
import scala.tools.eclipse.hyperlink.text.detector.DeclarationHyperlinkDetector

import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.eclipse.jdt.ui.actions.OpenAction

class HyperlinkOpenAction(editor: JavaEditor) extends OpenAction(editor) with HyperlinkOpenActionStrategy {

  override protected val detectionStrategy: BaseHyperlinkDetector = DeclarationHyperlinkDetector()

  override def run() { openHyperlink(editor) }
}