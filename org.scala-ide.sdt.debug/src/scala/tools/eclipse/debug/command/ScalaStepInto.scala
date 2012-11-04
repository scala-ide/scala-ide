package scala.tools.eclipse.debug.command

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
import scala.tools.eclipse.debug.BaseDebuggerActor

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
    val actor = new ScalaStepIntoActor(scalaStackFrame.getDebugTarget, scalaStackFrame.thread, stepIntoRequest, stepOutRequest, depth, scalaStackFrame.stackFrame.location.lineNumber) {
      override val scalaStep: ScalaStepInto = new ScalaStepInto(this)
    }
    actor.start()
    
    actor.scalaStep
  }

}

/**
 * A step into in the Scala debug model.
 * This class is thread safe. Instances have be created through its companion object.
 */
private class ScalaStepInto private (eventActor: ScalaStepIntoActor) extends BaseScalaStep[ScalaStepIntoActor](eventActor)

/**
 * Actor used to manage a Scala step into. It keeps track of the request needed to perform this step.
 * This class is thread safe. Instances are not to be created outside of the ScalaStepInto object.
 */
private[command] abstract class ScalaStepIntoActor(debugTarget: ScalaDebugTarget, thread: ScalaThread, stepIntoRequest: StepRequest, stepOutRequest: StepRequest, stackDepth: Int, stackLine: Int) extends BaseDebuggerActor {
  /**
   * Needed to perform a correct step out (see Eclipse bug report #38744)
   */
  private var stepOutStackDepth = 0
  
  protected[command] def scalaStep: ScalaStepInto

  override protected def postStart(): Unit = link(thread.eventActor)

  override protected def behavior = {
    // JDI event triggered when a step has been performed
    case stepEvent: StepEvent =>
      reply(stepEvent.request.asInstanceOf[StepRequest].depth match {
        case StepRequest.STEP_INTO =>
          if (debugTarget.stepFilters.isOpaqueLocation(stepEvent.location)) {
            // don't step deeper into constructor from 'hidden' entities
            stepOutStackDepth = stepEvent.thread.frameCount
            stepIntoRequest.disable()
            stepOutRequest.enable()
            false
          } else {
            if (!debugTarget.stepFilters.isTransparentLocation(stepEvent.location) && stepEvent.location.lineNumber != stackLine) {
              dispose()
              thread.suspendedFromScala(DebugEvent.STEP_INTO)
              true
            } 
            else false
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
  }

  private def step() {
    val eventDispatcher = debugTarget.eventDispatcher

    eventDispatcher.setActorFor(this, stepIntoRequest)
    eventDispatcher.setActorFor(this, stepOutRequest)
    stepIntoRequest.enable()
    thread.resumeFromScala(scalaStep, DebugEvent.STEP_INTO)
  }

  private def dispose(): Unit = {
    poison()
    unlink(thread.eventActor)
    val eventDispatcher = debugTarget.eventDispatcher
    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

    // make sure that actors are gracefully shut down
    eventDispatcher.unsetActorFor(stepIntoRequest)
    eventDispatcher.unsetActorFor(stepOutRequest)

    stepIntoRequest.disable()
    stepOutRequest.disable()
    eventRequestManager.deleteEventRequest(stepIntoRequest)
    eventRequestManager.deleteEventRequest(stepOutRequest)
  }
  
  override protected def preExit(): Unit = dispose()
}
