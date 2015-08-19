package org.scalaide.debug.internal.command

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.eclipse.debug.core.DebugEvent
import org.scalaide.debug.internal.JdiEventReceiver
import org.scalaide.debug.internal.model.JdiRequestFactory
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.model.ScalaStackFrame
import org.scalaide.debug.internal.model.ScalaThread

import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.StepRequest

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
    val subordinate = new ScalaStepIntoSubordinate(scalaStackFrame.getDebugTarget, scalaStackFrame.thread, stepIntoRequest, stepOutRequest, depth, scalaStackFrame.stackFrame.location.lineNumber)
    subordinate.scalaStep
  }

}

/**
 * Actor used to manage a Scala step into. It keeps track of the request needed to perform this step.
 * This class is thread safe. Instances are not to be created outside of the ScalaStepInto object.
 */
private[command] class ScalaStepIntoSubordinate(debugTarget: ScalaDebugTarget, thread: ScalaThread, stepIntoRequest: StepRequest, stepOutRequest: StepRequest, stackDepth: Int, stackLine: Int)
    extends ScalaStep with JdiEventReceiver {
  import scala.concurrent.ExecutionContext.Implicits.global

  /**
   * Needed to perform a correct step out (see Eclipse bug report #38744)
   */
  private val stepOutStackDepth = new AtomicInteger

  private val enabled = new AtomicBoolean

  protected[command] def scalaStep: ScalaStep = this

  override protected def innerHandle = {
    // JDI event triggered when a step has been performed
    case stepEvent: StepEvent =>
      stepEvent.request.asInstanceOf[StepRequest].depth match {
        case StepRequest.STEP_INTO =>
          if (debugTarget.cache.isOpaqueLocation(stepEvent.location)) {
            // don't step deeper into constructor from 'hidden' entities
            stepOutStackDepth.getAndSet(stepEvent.thread.frameCount)
            stepIntoRequest.disable()
            stepOutRequest.enable()
            false
          } else {
            if (!debugTarget.cache.isTransparentLocation(stepEvent.location) && stepEvent.location.lineNumber != stackLine) {
              disable()
              thread.suspendedFromScala(DebugEvent.STEP_INTO)
              true
            } else false
          }

        case StepRequest.STEP_OUT =>
          if (stepEvent.thread.frameCount == stackDepth && stepEvent.location.lineNumber != stackLine) {
            // we are back on the method, but on a different line, stopping the stepping
            disable()
            thread.suspendedFromScala(DebugEvent.STEP_INTO)
            true
          } else {
            // switch back to step into only if the step return has been effectively done.
            if (stepEvent.thread.frameCount < stepOutStackDepth.get) {
              // launch a new step into
              stepOutRequest.disable()
              stepIntoRequest.enable()
            }
            false
          }
      }
  }

  override def step(): Unit = Future {
    enable()
    thread.resumeFromScala(scalaStep, DebugEvent.STEP_INTO)
  }

  override def stop(): Unit = Future {
    disable()
  }

  private def enable(): Unit = {
    if (!enabled.getAndSet(true)) {
      val eventDispatcher = debugTarget.eventDispatcher
      eventDispatcher.register(this, stepIntoRequest)
      eventDispatcher.register(this, stepOutRequest)
      stepIntoRequest.enable()
    }
  }

  private def disable(): Unit = {
    if (enabled.getAndSet(false)) {
      val eventDispatcher = debugTarget.eventDispatcher
      val eventRequestManager = debugTarget.virtualMachine.eventRequestManager
      // make sure that actors are gracefully shut down
      eventDispatcher.unregister(stepIntoRequest)
      eventDispatcher.unregister(stepOutRequest)

      stepIntoRequest.disable()
      stepOutRequest.disable()
      eventRequestManager.deleteEventRequest(stepIntoRequest)
      eventRequestManager.deleteEventRequest(stepOutRequest)
    }
  }
}
