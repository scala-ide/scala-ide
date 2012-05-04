package scala.tools.eclipse.ui.actions

import scala.tools.eclipse.contribution.weaving.jdt.ui.actions.IOpenActionProvider
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.eclipse.jdt.ui.actions.OpenAction
import scala.tools.eclipse.hyperlink.text.detector.DeclarationHyperlinkDetector

class OpenActionProvider extends IOpenActionProvider {
  override def getOpenAction(editor: JavaEditor): OpenAction = new HyperlinkOpenAction(DeclarationHyperlinkDetector(), editor)
}