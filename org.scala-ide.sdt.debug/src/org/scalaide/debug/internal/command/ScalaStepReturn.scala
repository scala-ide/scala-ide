package org.scalaide.debug.internal.command

import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.eclipse.debug.core.DebugEvent
import org.scalaide.debug.internal.JdiEventReceiver
import org.scalaide.debug.internal.model.JdiRequestFactory
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.model.ScalaStackFrame
import org.scalaide.debug.internal.model.ScalaThread

import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.StepRequest

object ScalaStepReturn {
  def apply(scalaStackFrame: ScalaStackFrame): ScalaStep = {
    val stepReturnRequest = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_OUT, scalaStackFrame.thread)
    val subordinate = new ScalaStepReturnSubordinate(scalaStackFrame.getDebugTarget, scalaStackFrame.thread, stepReturnRequest)
    subordinate.scalaStep
  }
}

/**
 * Actor used to manage a Scala step return. It keeps track of the request needed to perform this step.
 * This class is thread safe. Instances are not to be created outside of the ScalaStepReturn object.
 */
private[command] class ScalaStepReturnSubordinate(debugTarget: ScalaDebugTarget, thread: ScalaThread, stepReturnRequest: StepRequest)
    extends ScalaStep with JdiEventReceiver {
  import scala.concurrent.ExecutionContext.Implicits.global
  private val enabled = new AtomicBoolean

  protected[command] def scalaStep: ScalaStep = this

  override protected def innerHandle = {
    // JDI event triggered when a step has been performed
    case stepEvent: StepEvent =>
      if (!debugTarget.cache.isTransparentLocation(stepEvent.location)) {
        disable()
        thread.suspendedFromScala(DebugEvent.STEP_RETURN)
        true
      } else false
  }

  override def step(): Unit = Future {
    enable()
    thread.resumeFromScala(scalaStep, DebugEvent.STEP_RETURN)
  }

  override def stop(): Unit = Future {
    disable()
  }

  private def enable(): Unit = {
    if (!enabled.getAndSet(true)) {
      debugTarget.eventDispatcher.register(this, stepReturnRequest)
      stepReturnRequest.enable()
    }
  }

  private def disable(): Unit = {
    if (enabled.getAndSet(false)) {
      stepReturnRequest.disable()
      debugTarget.eventDispatcher.unregister(stepReturnRequest)
      debugTarget.virtualMachine.eventRequestManager.deleteEventRequest(stepReturnRequest)
    }
  }
}
