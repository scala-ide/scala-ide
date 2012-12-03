package scala.tools.eclipse.debug
package command

import scala.tools.eclipse.debug.BaseDebuggerActor
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
private[command] abstract class ScalaStepReturnActor(debugTarget: ScalaDebugTarget, thread: ScalaThread, stepReturnRequest: StepRequest) extends ScalaStepActor {
  
  protected[command] def scalaStep: ScalaStep

  override protected def postStart(): Unit = link(thread.companionActor)

  override protected def behavior = {
    // JDI event triggered when a step has been performed
    case stepEvent: StepEvent =>
      reply {
        if (!debugTarget.stepFilters.isTransparentLocation(stepEvent.location)) {
          dispose()
          thread.suspendedFromScala(DebugEvent.STEP_RETURN)
          true
        } 
        else false
      }
    case ScalaStep.Step => step()    // user step request
    case ScalaStep.Stop => dispose() // step is terminated
  }

  private def step() {
    this.attach(stepReturnRequest, enableRequest = true)
    thread.resumeFromScala(scalaStep, DebugEvent.STEP_RETURN)
  }

  override protected def onDispose(): Unit = {
    poison()
    unlink(thread.companionActor)

    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager
    this.detach(stepReturnRequest, disableRequest = true)
    eventRequestManager.deleteEventRequest(stepReturnRequest)
  }
  
  override protected def preExit(): Unit = dispose()
}