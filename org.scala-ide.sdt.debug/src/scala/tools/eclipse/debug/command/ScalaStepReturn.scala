package scala.tools.eclipse.debug.command

import com.sun.jdi.event.Event
import com.sun.jdi.event.EventSet
import scala.tools.eclipse.debug.model.ScalaStackFrame
import com.sun.jdi.request.StepRequest
import com.sun.jdi.request.EventRequest
import scala.tools.eclipse.debug.model.ScalaThread
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.StepEvent
import scala.actors.Actor
import scala.tools.eclipse.debug.ActorExit

object ScalaStepReturn {

  def apply(scalaStackFrame: ScalaStackFrame): ScalaStepReturn = {

    val eventRequestManager = scalaStackFrame.stackFrame.virtualMachine.eventRequestManager

    val stepIntoRequest = eventRequestManager.createStepRequest(scalaStackFrame.stackFrame.thread, StepRequest.STEP_LINE, StepRequest.STEP_OUT)
    stepIntoRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

    new ScalaStepReturn(scalaStackFrame.getScalaDebugTarget, scalaStackFrame.thread, stepIntoRequest)
  }

}

// TODO: when implementing support without filtering, need to workaround problem reported in Eclipse bug #38744
class ScalaStepReturn(target: ScalaDebugTarget, thread: ScalaThread, stepReturnRequest: StepRequest) extends ScalaStep {

  // Members declared in scala.tools.eclipse.debug.command.ScalaStep

  def step() {
    val eventDispatcher = target.eventDispatcher

    eventDispatcher.setActorFor(eventActor, stepReturnRequest)
    stepReturnRequest.enable
    thread.resumedFromScala(DebugEvent.STEP_RETURN)
    thread.thread.resume
  }

  def stop() {
    val eventDispatcher = target.eventDispatcher

    val eventRequestManager = thread.thread.virtualMachine.eventRequestManager

    stepReturnRequest.disable
    eventDispatcher.unsetActorFor(stepReturnRequest)
    eventRequestManager.deleteEventRequest(stepReturnRequest)

    eventActor ! ActorExit
  }

  // --------------------

  /**
   *  process the debug events
   */  
  val eventActor = new Actor {
    start
    def act() {
      loop {
        react {
          case stepEvent: StepEvent =>
            reply(
              if (target.isValidLocation(stepEvent.location)) {
                stop
                thread.suspendedFromScala(DebugEvent.STEP_RETURN)
                true
              } else {
                false
              })
          case ActorExit =>
            exit
        }
      }
    }
  }

}