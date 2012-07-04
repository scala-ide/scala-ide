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
import com.sun.jdi.request.EventRequestManager
import scala.tools.eclipse.debug.model.JdiRequestFactory

object ScalaStepReturn {

  def apply(scalaStackFrame: ScalaStackFrame): ScalaStepReturn = {

    val stepReturnRequest = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_OUT, scalaStackFrame.thread)

    val actor= new ScalaStepReturnActor(scalaStackFrame.debugTarget, scalaStackFrame.thread, stepReturnRequest)
    
    val step= new ScalaStepReturn(actor)
    actor.start(step)
    step
  }

}

// TODO: when implementing support without filtering, need to workaround problem reported in Eclipse bug #38744
class ScalaStepReturn private (eventActor: ScalaStepReturnActor) extends ScalaStep {

  // Members declared in scala.tools.eclipse.debug.command.ScalaStep
  
  def step() {
    eventActor ! ScalaStep.Step
  }
  
  def stop() {
    eventActor ! ScalaStep.Stop
  }

  // --------------------

}

private[command] class ScalaStepReturnActor(debugTarget: ScalaDebugTarget, thread: ScalaThread, stepReturnRequest: StepRequest) extends Actor {
  
  private var scalaStep: ScalaStepReturn = _
  
  def start(step: ScalaStepReturn) {
    scalaStep= step
    start()
  }
  
  def act() {
    loop {
      react {
        // JDI event triggered when a step has been performed
        case stepEvent: StepEvent =>
          reply(
            if (debugTarget.isValidLocation(stepEvent.location)) {
              dispose
              thread.suspendedFromScala(DebugEvent.STEP_RETURN)
              true
            } else {
              false
            })
        // user step request
        case ScalaStep.Step =>
          step
        // step is terminated
        case ScalaStep.Stop =>
          dispose
        case ActorExit =>
          exit
      }
    }
  }

  private def step() {
    val eventDispatcher = debugTarget.eventDispatcher

    eventDispatcher.setActorFor(this, stepReturnRequest)
    stepReturnRequest.enable
    thread.resumeFromScala(scalaStep, DebugEvent.STEP_RETURN)
  }

  private def dispose() = {
    val eventDispatcher = debugTarget.eventDispatcher

    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

    stepReturnRequest.disable
    eventDispatcher.unsetActorFor(stepReturnRequest)
    eventRequestManager.deleteEventRequest(stepReturnRequest)

    this ! ActorExit
  }

}