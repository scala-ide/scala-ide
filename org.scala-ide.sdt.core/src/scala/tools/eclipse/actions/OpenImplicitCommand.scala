package scala.tools.eclipse.actions

import scala.tools.eclipse.ScalaSourceFileEditor
import scala.tools.eclipse.hyperlink.text.detector.ImplicitHyperlinkDetector
import scala.tools.eclipse.refactoring.EditorHelpers
import scala.tools.eclipse.ui.actions.HyperlinkOpenAction

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.ui.handlers.HandlerUtil

class OpenImplicitCommand extends AbstractHandler {
  override def execute(event: ExecutionEvent): Object = {
    HandlerUtil.getActiveEditor(event) match {
      case editor: ScalaSourceFileEditor =>
        new HyperlinkOpenAction(ImplicitHyperlinkDetector(), editor).run()
      case _ => ()
    }
    null
  }
  
  override def isEnabled: Boolean = EditorHelpers.withCurrentEditor { editor =>
    Some(new HyperlinkOpenAction(ImplicitHyperlinkDetector(), editor).isEnabled)
  }.getOrElse(false)
}