package scala.tools.eclipse.debug.command

import scala.tools.eclipse.debug.model.ScalaStackFrame
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.Event
import com.sun.jdi.request.StepRequest
import com.sun.jdi.request.EventRequest
import scala.tools.eclipse.debug.model.ScalaThread
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.StepEvent
import scala.actors.Actor
import scala.tools.eclipse.debug.ActorExit

object ScalaStepInto {

  /*
   * Initialize a Scala step into
   */
  def apply(scalaStackFrame: ScalaStackFrame): ScalaStepInto = {

    val eventRequestManager = scalaStackFrame.getScalaDebugTarget.virtualMachine.eventRequestManager

    val stepIntoRequest = eventRequestManager.createStepRequest(scalaStackFrame.stackFrame.thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO)
    stepIntoRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

    val stepOutRequest = eventRequestManager.createStepRequest(scalaStackFrame.stackFrame.thread, StepRequest.STEP_LINE, StepRequest.STEP_OUT)
    stepOutRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

    new ScalaStepInto(scalaStackFrame.getScalaDebugTarget, scalaStackFrame.thread, stepIntoRequest, stepOutRequest, scalaStackFrame.thread.getStackFrames.indexOf(scalaStackFrame), scalaStackFrame.stackFrame.location.lineNumber)
  }

}

class ScalaStepInto(target: ScalaDebugTarget, thread: ScalaThread, stepIntoRequest: StepRequest, stepOutRequest: StepRequest, stackDepth: Int, stackLine: Int) extends ScalaStep {

  // Members declared in scala.tools.eclipse.debug.command.ScalaStep

  def step() {
    val eventDispatcher = target.eventDispatcher

    eventDispatcher.setActorFor(eventActor, stepIntoRequest)
    eventDispatcher.setActorFor(eventActor, stepOutRequest)
    stepIntoRequest.enable
    thread.resumedFromScala(DebugEvent.STEP_INTO)
    thread.thread.resume
  }

  def stop() {
    val eventDispatcher = target.eventDispatcher

    val eventRequestManager = thread.thread.virtualMachine.eventRequestManager

    stepIntoRequest.disable
    stepOutRequest.disable
    eventDispatcher.unsetActorFor(stepIntoRequest)
    eventDispatcher.unsetActorFor(stepOutRequest)
    eventRequestManager.deleteEventRequest(stepIntoRequest)
    eventRequestManager.deleteEventRequest(stepOutRequest)

    eventActor ! ActorExit
    
  }

  // -----

  /**
   * Needed to perform a correct step out (see Eclipse bug report #38744)
   */
  var stepOutStackDepth = 0

  /**
   *  process the debug events
   */
  val eventActor = new Actor {
    start
    def act() {
      loop {
        react {
          case stepEvent: StepEvent =>
            reply(stepEvent.request match {
              case `stepIntoRequest` =>
                if (target.isValidLocation(stepEvent.location)) {
                  stop
                  thread.suspendedFromScala(DebugEvent.STEP_INTO)
                  true
                } else {
                  if (target.shouldNotStepInto(stepEvent.location)) {
                    // don't step deeper into constructor from 'hidden' entities
                    stepOutStackDepth = stepEvent.thread.frameCount
                    stepIntoRequest.disable
                    stepOutRequest.enable
                  }
                  false
                }
              case `stepOutRequest` =>
                if (stepEvent.thread.frameCount == stackDepth && stepEvent.location.lineNumber != stackLine) {
                  // we are back on the method, but on a different line, stopping the stepping
                  stop
                  thread.suspendedFromScala(DebugEvent.STEP_INTO)
                  true
                } else {
                  // switch back to step into only if the step return has been effectively done.
                  if (stepEvent.thread.frameCount < stepOutStackDepth) {
                    // launch a new step into
                    stepOutRequest.disable
                    stepIntoRequest.enable
                  }
                  false
                }
            })
          case ActorExit =>
            exit
        }
      }
    }
  }

}