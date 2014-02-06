package org.scalaide.ui.internal.actions.hyperlinks

import org.scalaide.ui.internal.editor.ScalaSourceFileEditor
import org.scalaide.core.hyperlink.detector.BaseHyperlinkDetector
import org.scalaide.core.hyperlink.detector.ImplicitHyperlinkDetector
import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.ui.handlers.HandlerUtil

class OpenImplicitCommand extends AbstractHandler with HyperlinkOpenActionStrategy {
  override val detectionStrategy: BaseHyperlinkDetector = ImplicitHyperlinkDetector()

  override def execute(event: ExecutionEvent): Object = {
    HandlerUtil.getActiveEditor(event) match {
      case editor: ScalaSourceFileEditor =>
        openHyperlink(editor)
      case _ => ()
    }
    null
  }
}
