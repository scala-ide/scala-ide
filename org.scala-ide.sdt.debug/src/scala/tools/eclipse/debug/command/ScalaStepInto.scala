package scala.tools.eclipse.debug.command

import scala.tools.eclipse.debug.model.ScalaStackFrame
import org.eclipse.jdt.internal.debug.core.IJDIEventListener
import com.sun.jdi.event.EventSet
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget
import com.sun.jdi.event.Event
import com.sun.jdi.request.StepRequest
import com.sun.jdi.request.EventRequest
import scala.tools.eclipse.debug.model.ScalaThread
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import org.eclipse.jdt.internal.debug.core.EventDispatcher
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.StepEvent

object ScalaStepInto {

  def apply(scalaStackFrame: ScalaStackFrame): ScalaStepInto = {

    val eventRequestManager = scalaStackFrame.stackFrame.virtualMachine.eventRequestManager

    val stepIntoRequest = eventRequestManager.createStepRequest(scalaStackFrame.stackFrame.thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO)
    stepIntoRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

    val stepOutRequest = eventRequestManager.createStepRequest(scalaStackFrame.stackFrame.thread, StepRequest.STEP_LINE, StepRequest.STEP_OUT)
    stepOutRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

    new ScalaStepInto(scalaStackFrame.getScalaDebugTarget, scalaStackFrame.thread, stepIntoRequest, stepOutRequest, scalaStackFrame.thread.getStackFrames.indexOf(scalaStackFrame), scalaStackFrame.stackFrame.location.lineNumber)
  }

}

class ScalaStepInto(target: ScalaDebugTarget, thread: ScalaThread, stepIntoRequest: StepRequest, stepOutRequest: StepRequest, stackDepth: Int, stackLine: Int) extends IJDIEventListener with ScalaStep {

  // Members declared in org.eclipse.jdt.internal.debug.core.IJDIEventListener

  def eventSetComplete(event: Event, target: JDIDebugTarget, suspend: Boolean, eventSet: EventSet): Unit = {
    // nothing to do
  }

  def handleEvent(event: Event, javaTarget: JDIDebugTarget, suspendVote: Boolean, eventSet: EventSet): Boolean = {
    event match {
      case stepEvent: StepEvent =>
        event.request match {
          case `stepIntoRequest` =>
            if (target.isValidLocation(stepEvent.location)) {
              stop
              thread.suspendedFromScala(DebugEvent.STEP_INTO)
              false
            } else {
              if (target.shouldNotStepInto(stepEvent.location)) {
                // don't step deeper into constructor from 'hidden' entities
                stepOutStackDepth = stepEvent.thread.frameCount
                stepIntoRequest.disable
                stepOutRequest.enable
              }
              true
            }
          case `stepOutRequest` =>
            if (stepEvent.thread.frameCount == stackDepth && stepEvent.location.lineNumber != stackLine) {
              // we are back on the method, but on a different line, stopping the stepping
              stop
              thread.suspendedFromScala(DebugEvent.STEP_INTO)
              false
            } else {
              // switch back to step into only if the step return has been effectively done.
              if (stepEvent.thread.frameCount < stepOutStackDepth) {
                // launch a new step into
                stepOutRequest.disable
                stepIntoRequest.enable
              }
              true
            }
        }
      case _ =>
        suspendVote
    }
  }

  // Members declared in scala.tools.eclipse.debug.command.ScalaStep

  def step() {
    val eventDispatcher = target.javaTarget.getEventDispatcher

    eventDispatcher.addJDIEventListener(this, stepIntoRequest)
    eventDispatcher.addJDIEventListener(this, stepOutRequest)
    stepIntoRequest.enable
    thread.resumedFromScala(DebugEvent.STEP_INTO)
    thread.thread.resume
  }

  def stop() {
    val eventDispatcher = target.javaTarget.getEventDispatcher

    val eventRequestManager = thread.thread.virtualMachine.eventRequestManager

    stepIntoRequest.disable
    stepOutRequest.disable
    eventDispatcher.removeJDIEventListener(this, stepIntoRequest)
    eventDispatcher.removeJDIEventListener(this, stepOutRequest)
    eventRequestManager.deleteEventRequest(stepIntoRequest)
    eventRequestManager.deleteEventRequest(stepOutRequest)

  }

  // -----

  /**
   * Needed to perform a correct step out (see Eclipse bug report #38744)
   */
  var stepOutStackDepth = 0

}