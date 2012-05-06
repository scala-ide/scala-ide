package scala.tools.eclipse.actions

import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.ui.handlers.HandlerUtil
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import scala.tools.eclipse.ui.actions.HyperlinkOpenAction
import scala.tools.eclipse.hyperlink.text.detector.ImplicitHyperlinkDetector

class OpenImplicitCommand extends AbstractHandler {
  override def execute(event: ExecutionEvent): Object = {
    HandlerUtil.getActiveEditor(event) match {
      case editor: ScalaSourceFileEditor =>
        new HyperlinkOpenAction(ImplicitHyperlinkDetector(), editor).run()
      case _ => ()
    }
    null
  }
}