package org.scalaide.debug.internal.ui

import scala.util.Failure
import scala.util.Success

import org.eclipse.jdt.internal.debug.core.breakpoints.JavaLineBreakpoint
import org.eclipse.jface.dialogs.MessageDialog
import org.scalaide.debug.BreakpointContext
import org.scalaide.debug.DebugContext
import org.scalaide.debug.DebugEventHandler
import org.scalaide.debug.internal.expression.ExpressionManager
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.util.eclipse.SWTUtils
import org.scalaide.util.ui.DisplayThread

import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.Event

class BreakpointEventHandler extends DebugEventHandler {

  override def handleEvent(event: Event, context: DebugContext) = event match {
    case event: BreakpointEvent ⇒
      context match {
        case BreakpointContext(breakpoint, debugTarget) ⇒
          breakpoint match {
            case breakpoint: JavaLineBreakpoint ⇒
              handleBreakpointEvent(event, breakpoint, debugTarget)
            case _ ⇒
              None
          }
        case _ ⇒
          None
      }
    case _ ⇒
      None
  }

  private def handleBreakpointEvent(event: BreakpointEvent, breakpoint: JavaLineBreakpoint, debugTarget: ScalaDebugTarget): Option[Boolean] = {
    val condition = getCondition(breakpoint)
    val location = event.location()
    val thread = event.thread()

    ExpressionManager.shouldSuspendVM(condition, location, thread, debugTarget.classPath) match {
      case Success(true) =>
        None
      case Success(false) =>
        Some(false)
      case Failure(e: VMDisconnectedException) =>
        // Ok, end of debugging
        Some(false)
      case Failure(e) =>
        DisplayThread.asyncExec {
          MessageDialog.openError(
            SWTUtils.getShell,
            "Error",
            s"Error in conditional breakpoint:\n${e.getMessage}")
        }
        None
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
