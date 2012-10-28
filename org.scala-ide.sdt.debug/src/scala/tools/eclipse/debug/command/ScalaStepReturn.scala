package scala.tools.eclipse.debug.command

import scala.actors.Actor
import scala.tools.eclipse.debug.ActorExit
import scala.tools.eclipse.debug.model.JdiRequestFactory
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import scala.tools.eclipse.debug.model.ScalaStackFrame
import scala.tools.eclipse.debug.model.ScalaThread
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.StepRequest
import scala.tools.eclipse.debug.model.StepFilters

object ScalaStepReturn {

  def apply(scalaStackFrame: ScalaStackFrame): ScalaStep = {

    val stepReturnRequest = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_OUT, scalaStackFrame.thread)

    val actor= new ScalaStepReturnActor(scalaStackFrame.getDebugTarget, scalaStackFrame.thread, stepReturnRequest)
    
    val step= new ScalaStepReturn(actor)
    actor.start(step)
    step
  }

}

/**
 * A step return in the Scala debug model.
 * This class is thread safe. Instances have be created through its companion object.
 */
private class ScalaStepReturn private (eventActor: ScalaStepReturnActor) extends ScalaStep {
// TODO: when implementing support without filtering, need to workaround problem reported in Eclipse bug #38744

  // Members declared in scala.tools.eclipse.debug.command.ScalaStep
  
  def step() {
    eventActor ! ScalaStep.Step
  }
  
  def stop() {
    eventActor ! ScalaStep.Stop
  }

  // --------------------

}

/**
 * Actor used to manage a Scala step return. It keeps track of the request needed to perform this step.
 * This class is thread safe. Instances are not to be created outside of the ScalaStepReturn object.
 */
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
            if (!StepFilters.isTransparentLocation(stepEvent.location)) {
              dispose()
              thread.suspendedFromScala(DebugEvent.STEP_RETURN)
              true
            } else {
              false
            })
        // user step request
        case ScalaStep.Step =>
          step()
        // step is terminated
        case ScalaStep.Stop =>
          dispose()
        case ActorExit =>
          exit()
      }
    }
  }

  private def step() {
    val eventDispatcher = debugTarget.eventDispatcher

    eventDispatcher.setActorFor(this, stepReturnRequest)
    stepReturnRequest.enable()
    thread.resumeFromScala(scalaStep, DebugEvent.STEP_RETURN)
  }

  private def dispose() = {
    val eventDispatcher = debugTarget.eventDispatcher

    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

    stepReturnRequest.disable()
    eventDispatcher.unsetActorFor(stepReturnRequest)
    eventRequestManager.deleteEventRequest(stepReturnRequest)

    this ! ActorExit
  }

}