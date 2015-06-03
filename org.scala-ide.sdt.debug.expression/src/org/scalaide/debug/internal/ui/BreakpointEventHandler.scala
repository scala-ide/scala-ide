package org.scalaide.debug.internal.ui

import scala.util.Failure
import scala.util.Success
import org.eclipse.jdt.internal.debug.core.breakpoints.JavaLineBreakpoint
import org.eclipse.jface.dialogs.MessageDialog
import org.scalaide.debug.BreakpointContext
import org.scalaide.debug.ContinueExecution
import org.scalaide.debug.DebugContext
import org.scalaide.debug.DebugEventHandler
import org.scalaide.debug.JdiEventCommand
import org.scalaide.debug.NoCommand
import org.scalaide.debug.SuspendExecution
import org.scalaide.debug.internal.expression.ExpressionManager
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.util.eclipse.SWTUtils
import org.scalaide.util.Utils.jdiSynchronized
import org.scalaide.util.ui.DisplayThread
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.Event

class BreakpointEventHandler extends DebugEventHandler {

  override def handleEvent(event: Event, context: DebugContext) = (event, context) match {
    case (event: BreakpointEvent, BreakpointContext(breakpoint: JavaLineBreakpoint, debugTarget)) ⇒
       jdiSynchronized { handleBreakpointEvent(event, breakpoint, debugTarget) }
    case _ ⇒
      NoCommand
  }

  private def handleBreakpointEvent(event: BreakpointEvent, breakpoint: JavaLineBreakpoint, debugTarget: ScalaDebugTarget): JdiEventCommand = {
    val condition = getCondition(breakpoint)
    val location = event.location()
    val thread = event.thread()

    ExpressionManager.shouldSuspendVM(condition, location, thread, debugTarget.classPath) match {
      case Success(true) =>
        SuspendExecution
      case Success(false) =>
        ContinueExecution
      case Failure(e: VMDisconnectedException) =>
        // Ok, end of debugging
        ContinueExecution
      case Failure(e) =>
        DisplayThread.asyncExec {
          MessageDialog.openError(
            SWTUtils.getShell,
            "Error",
            s"Error in conditional breakpoint:\n${e.getMessage}")
        }
        SuspendExecution
    }
  }

  /**
   * Extracts condition from breakpoint.
   * Treats `null` condition and whitespace-only condition as empty.
   */
  private def getCondition(lineBreakpoint: JavaLineBreakpoint): Option[String] = {
    val condition = lineBreakpoint.getCondition
    def isConditionNonEmpty = lineBreakpoint.hasCondition && condition != null && !condition.trim.isEmpty
    if (isConditionNonEmpty) Some(condition) else None
  }

}
