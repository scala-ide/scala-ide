package scala.tools.eclipse.debug.command

import scala.actors.Actor
import scala.tools.eclipse.debug.ActorExit
import scala.tools.eclipse.debug.model.JdiRequestFactory
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import scala.tools.eclipse.debug.model.ScalaStackFrame
import scala.tools.eclipse.debug.model.ScalaThread
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.StepRequest
import com.sun.jdi.event.Event
import scala.tools.eclipse.debug.model.StepFilters
import scala.tools.eclipse.logging.HasLogger

object ScalaStepInto {

  /*
   * Initialize a Scala step into
   */
  def apply(scalaStackFrame: ScalaStackFrame): ScalaStep = {

    // we noticed that STEP_LINE would miss events for stepping into BoxesRunTime. Might be because the
    // file has no source file information, need to check
    val stepIntoRequest = JdiRequestFactory.createStepRequest(StepRequest.STEP_MIN, StepRequest.STEP_INTO, scalaStackFrame.thread)
    stepIntoRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

    val stepOutRequest = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_OUT, scalaStackFrame.thread)
    stepOutRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

    val stackFrames = scalaStackFrame.thread.getStackFrames
    val depth = stackFrames.length - stackFrames.indexOf(scalaStackFrame)
    val actor = new ScalaStepIntoActor(scalaStackFrame.debugTarget, scalaStackFrame.thread, stepIntoRequest, stepOutRequest, depth, scalaStackFrame.stackFrame.location.lineNumber)

    val step= new ScalaStepInto(actor)
    actor.start(step)
    step
  }

}

/**
 * A step into in the Scala debug model.
 * This class is thread safe. Instances have be created through its companion object.
 */
private class ScalaStepInto private (eventActor: ScalaStepIntoActor) extends ScalaStep {

  // Members declared in scala.tools.eclipse.debug.command.ScalaStep

  def step() {
    eventActor ! ScalaStep.Step
  }

  def stop() {
    eventActor ! ScalaStep.Stop
  }

  // ------

}

/**
 * Actor used to manage a Scala step into. It keeps track of the request needed to perform this step.
 * This class is thread safe. Instances are not to be created outside of the ScalaStepInto object.
 */
private[command] class ScalaStepIntoActor(debugTarget: ScalaDebugTarget, thread: ScalaThread, stepIntoRequest: StepRequest, stepOutRequest: StepRequest, stackDepth: Int, stackLine: Int) extends Actor with HasLogger {

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
              if (StepFilters.isOpaqueLocation(stepEvent.location)) {
                  // don't step deeper into constructor from 'hidden' entities
                  stepOutStackDepth = stepEvent.thread.frameCount
                  stepIntoRequest.disable()
                  stepOutRequest.enable()
                  false
                } else {
                  if (!StepFilters.isTransparentLocation(stepEvent.location) && stepEvent.location.lineNumber != stackLine) {
                    dispose()
                    thread.suspendedFromScala(DebugEvent.STEP_INTO)
                    true
                  } else
                    false
                }

            case StepRequest.STEP_OUT =>
              if (stepEvent.thread.frameCount == stackDepth && stepEvent.location.lineNumber != stackLine) {
                // we are back on the method, but on a different line, stopping the stepping
                dispose()
                thread.suspendedFromScala(DebugEvent.STEP_INTO)
                true
              } else {
                // switch back to step into only if the step return has been effectively done.
                if (stepEvent.thread.frameCount < stepOutStackDepth) {
                  // launch a new step into
                  stepOutRequest.disable()
                  stepIntoRequest.enable()
                }
                false
              }
          })
        // user step request
        case ScalaStep.Step =>
          step()
        case ScalaStep.Stop =>
          dispose()
        // step is terminated
        case ActorExit =>
          exit()
        case event: Event =>
          logger.debug("untreated event! " + event)
      }
    }
  }

  private def step() {
    val eventDispatcher = debugTarget.eventDispatcher

    eventDispatcher.setActorFor(this, stepIntoRequest)
    eventDispatcher.setActorFor(this, stepOutRequest)
    stepIntoRequest.enable()
    thread.resumeFromScala(scalaStep, DebugEvent.STEP_INTO)
  }

  private def dispose() {
    val eventDispatcher = debugTarget.eventDispatcher

    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

    stepIntoRequest.disable()
    stepOutRequest.disable()
    eventDispatcher.unsetActorFor(stepIntoRequest)
    eventDispatcher.unsetActorFor(stepOutRequest)
    eventRequestManager.deleteEventRequest(stepIntoRequest)
    eventRequestManager.deleteEventRequest(stepOutRequest)
    
    this ! ActorExit
  }
}
