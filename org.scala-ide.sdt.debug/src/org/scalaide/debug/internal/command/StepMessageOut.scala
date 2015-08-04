package org.scalaide.debug.internal.command

import org.eclipse.core.commands.IHandler
import org.eclipse.debug.core.commands.ITerminateHandler
import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.ui.handlers.HandlerUtil
import org.eclipse.jface.viewers.IStructuredSelection
import org.scalaide.debug.internal.model.ScalaStackFrame

class StepMessageOut extends AbstractHandler {
  override def execute(event: ExecutionEvent): Object = {
    val window = HandlerUtil.getActiveWorkbenchWindow(event)
    val service = DebugUITools.getDebugContextManager().getContextService(window)
    val selection = service.getActiveContext()

    selection match {
      case se: IStructuredSelection =>
        se.getFirstElement() match {
          case ssf: ScalaStackFrame =>
            ssf.thread.stepMessageOut()
          case _ =>
        }
      case _ =>
    }
    null
  }

  override def setEnabled(context: Object): Unit = ()

  override def isEnabled() = true
}
