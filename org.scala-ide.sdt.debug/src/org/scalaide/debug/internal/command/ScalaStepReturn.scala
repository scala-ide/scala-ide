package org.scalaide.debug.internal.command

import org.scalaide.debug.internal.BaseDebuggerActor
import org.scalaide.debug.internal.model.JdiRequestFactory
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.model.ScalaStackFrame
import org.scalaide.debug.internal.model.ScalaThread
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.StepRequest

object ScalaStepReturn {
  def apply(scalaStackFrame: ScalaStackFrame): ScalaStep = {
    val stepReturnRequest = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_OUT, scalaStackFrame.thread)

    val companionActor = new ScalaStepReturnActor(scalaStackFrame.getDebugTarget, scalaStackFrame.thread, stepReturnRequest) {
      // TODO: when implementing support without filtering, need to workaround problem reported in Eclipse bug #38744
      override val scalaStep: ScalaStep = new ScalaStepImpl(this)
    }
    companionActor.start()

    companionActor.scalaStep
  }
}

/**
 * Actor used to manage a Scala step return. It keeps track of the request needed to perform this step.
 * This class is thread safe. Instances are not to be created outside of the ScalaStepReturn object.
 */
private[command] abstract class ScalaStepReturnActor(debugTarget: ScalaDebugTarget, thread: ScalaThread, stepReturnRequest: StepRequest) extends BaseDebuggerActor {

  private var enabled = false

  protected[command] def scalaStep: ScalaStep

  override protected def postStart(): Unit = link(thread.companionActor)

  override protected def behavior = {
    // JDI event triggered when a step has been performed
    case stepEvent: StepEvent =>
      reply {
        if (!debugTarget.cache.isTransparentLocation(stepEvent.location)) {
          terminate()
          thread.suspendedFromScala(DebugEvent.STEP_RETURN)
          true
        }
        else false
      }
    case ScalaStep.Step => step()    // user step request
    case ScalaStep.Stop => terminate() // step is terminated
  }

  private def step(): Unit = {
    enable()
    thread.resumeFromScala(scalaStep, DebugEvent.STEP_RETURN)
  }

  private def terminate(): Unit = {
    disable()
    poison()
  }

  private def enable(): Unit = {
    if (!enabled) {
      debugTarget.eventDispatcher.setActorFor(this, stepReturnRequest)
      stepReturnRequest.enable()
      enabled = true
    }
  }

  private def disable(): Unit = {
    if (enabled) {

      stepReturnRequest.disable()
      debugTarget.eventDispatcher.unsetActorFor(stepReturnRequest)
      debugTarget.virtualMachine.eventRequestManager.deleteEventRequest(stepReturnRequest)
      enabled = false
    }
  }

  override protected def preExit(): Unit = {
    unlink(thread.companionActor)
    disable()
  }
}