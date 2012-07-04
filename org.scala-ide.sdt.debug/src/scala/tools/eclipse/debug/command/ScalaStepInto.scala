package scala.tools.eclipse.debug.command

import scala.actors.Actor
import scala.tools.eclipse.debug.ActorExit
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import scala.tools.eclipse.debug.model.ScalaStackFrame
import scala.tools.eclipse.debug.model.ScalaThread
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.StepRequest
import scala.tools.eclipse.debug.model.JdiRequestFactory

object ScalaStepInto {

  /*
   * Initialize a Scala step into
   */
  def apply(scalaStackFrame: ScalaStackFrame): ScalaStepInto = {

    val stepIntoRequest = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_INTO, scalaStackFrame.thread)
    stepIntoRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

    val stepOutRequest = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_OUT, scalaStackFrame.thread)
    stepOutRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

    val actor = new ScalaStepIntoActor(scalaStackFrame.debugTarget, scalaStackFrame.thread, stepIntoRequest, stepOutRequest, scalaStackFrame.thread.getStackFrames.indexOf(scalaStackFrame), scalaStackFrame.stackFrame.location.lineNumber)

    val step= new ScalaStepInto(actor)
    actor.start(step)
    step
  }

}

class ScalaStepInto private (eventActor: ScalaStepIntoActor) extends ScalaStep {

  // Members declared in scala.tools.eclipse.debug.command.ScalaStep

  def step() {
    eventActor ! ScalaStep.Step
  }

  def stop() {
    eventActor ! ScalaStep.Stop
  }

  // ------

}

private[command] class ScalaStepIntoActor(debugTarget: ScalaDebugTarget, thread: ScalaThread, stepIntoRequest: StepRequest, stepOutRequest: StepRequest, stackDepth: Int, stackLine: Int) extends Actor {

  /**
   * Needed to perform a correct step out (see Eclipse bug report #38744)
   */
  private var stepOutStackDepth = 0
  
  private var scalaStep: ScalaStepInto = _
  
  def start(step: ScalaStepInto) {
    scalaStep= step
    start()
  }

  def act() {
    loop {
      react {
        // JDI event triggered when a step has been performed
        case stepEvent: StepEvent =>
          reply(stepEvent.request.asInstanceOf[StepRequest].depth match {
            case StepRequest.STEP_INTO =>
              if (debugTarget.isValidLocation(stepEvent.location)) {
                dispose
                thread.suspendedFromScala(DebugEvent.STEP_INTO)
                true
              } else {
                if (debugTarget.shouldNotStepInto(stepEvent.location)) {
                  // don't step deeper into constructor from 'hidden' entities
                  stepOutStackDepth = stepEvent.thread.frameCount
                  stepIntoRequest.disable
                  stepOutRequest.enable
                }
                false
              }
            case StepRequest.STEP_OUT =>
              if (stepEvent.thread.frameCount == stackDepth && stepEvent.location.lineNumber != stackLine) {
                // we are back on the method, but on a different line, stopping the stepping
                dispose
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
        // user step request
        case ScalaStep.Step =>
          step
        case ScalaStep.Stop =>
          dispose
        // step is terminated
        case ActorExit =>
          exit
      }
    }
  }

  private def step() {
    val eventDispatcher = debugTarget.eventDispatcher

    eventDispatcher.setActorFor(this, stepIntoRequest)
    eventDispatcher.setActorFor(this, stepOutRequest)
    stepIntoRequest.enable
    thread.resumeFromScala(scalaStep, DebugEvent.STEP_INTO)
  }

  private def dispose() {
    val eventDispatcher = debugTarget.eventDispatcher

    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

    stepIntoRequest.disable
    stepOutRequest.disable
    eventDispatcher.unsetActorFor(stepIntoRequest)
    eventDispatcher.unsetActorFor(stepOutRequest)
    eventRequestManager.deleteEventRequest(stepIntoRequest)
    eventRequestManager.deleteEventRequest(stepOutRequest)
    
    this ! ActorExit
  }
}
