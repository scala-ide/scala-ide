package scala.tools.eclipse.ui.actions

import scala.tools.eclipse.contribution.weaving.jdt.ui.actions.IOpenActionProvider
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.eclipse.jdt.ui.actions.OpenAction

class OpenActionProvider extends IOpenActionProvider {
  override def getOpenAction(editor: JavaEditor): OpenAction = new HyperlinkOpenAction(editor)
}