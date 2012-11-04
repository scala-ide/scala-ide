package scala.tools.eclipse.debug.command

import scala.Option.option2Iterable
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.debug.JDIUtil.methodToLines
import scala.tools.eclipse.debug.model.{ScalaThread, ScalaStackFrame, ScalaDebugTarget}
import scala.tools.eclipse.debug.model.JdiRequestFactory
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.{ StepEvent, ClassPrepareEvent, BreakpointEvent }
import com.sun.jdi.request.{ StepRequest, EventRequest }
import scala.tools.eclipse.debug.model.StepFilters
import scala.tools.eclipse.debug.BaseDebuggerActor

object ScalaStepOver {

  final val LINE_NUMBER_UNAVAILABLE = -1

  def apply(scalaStackFrame: ScalaStackFrame): ScalaStep = {

    import scala.collection.JavaConverters._

    val location = scalaStackFrame.stackFrame.location

    val stepOverRequest = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_OVER, scalaStackFrame.thread)

    val requests = ListBuffer[EventRequest](stepOverRequest)

    val actor = if (location.lineNumber == LINE_NUMBER_UNAVAILABLE) {

      new ScalaStepOverActor(scalaStackFrame.getDebugTarget, null, scalaStackFrame.thread, requests) {
        override val scalaStep: ScalaStepOver = new ScalaStepOver(this)
      }

    } else {

      val classPrepareRequest = JdiRequestFactory.createClassPrepareRequest(location.declaringType.name + "$*", scalaStackFrame.getDebugTarget)

      requests += classPrepareRequest

      // find anonFunction in range
      val currentMethodLastLine = methodToLines(location.method).max

      val range = Range(location.lineNumber, (location.method.declaringType.methods.asScala.flatten(methodToLines(_)).filter(_ > currentMethodLastLine) :+ Int.MaxValue).min)

      // TODO: nestedTypes triggers a AllClasses request to the VM. Having the list of nested types managed and cached by the debug target should be more effective.
      val loadedAnonFunctionsInRange = location.method.declaringType.nestedTypes.asScala.flatMap(scalaStackFrame.getDebugTarget.stepFilters.anonFunctionsInRange(_, range))

      // if we are in an anonymous function, add the method
      if (location.declaringType.name.contains("$$anonfun$")) {
        loadedAnonFunctionsInRange ++= scalaStackFrame.getDebugTarget.stepFilters.findAnonFunction(location.declaringType)
      }

      requests ++= loadedAnonFunctionsInRange.map(JdiRequestFactory.createMethodEntryBreakpoint(_, scalaStackFrame.thread))

      new ScalaStepOverActor(scalaStackFrame.getDebugTarget, range, scalaStackFrame.thread, requests) {
        override val scalaStep: ScalaStepOver = new ScalaStepOver(this)
      }
    }

    actor.start()
    actor.scalaStep
  }

}

/**
 * A step over in the Scala debug model.
 * This class is thread safe. Instances have be created through its companion object.
 */
private class ScalaStepOver private (eventActor: ScalaStepOverActor) extends BaseScalaStep[ScalaStepOverActor](eventActor)

/**
 * Actor used to manage a Scala step over. It keeps track of the request needed to perform this step.
 * This class is thread safe. Instances are not to be created outside of the ScalaStepOver object.
 */
private[command] abstract class ScalaStepOverActor(debugTarget: ScalaDebugTarget, range: Range, thread: ScalaThread, requests: ListBuffer[EventRequest]) extends BaseDebuggerActor {

  protected[command] def scalaStep: ScalaStepOver

  override protected def postStart(): Unit = link(thread.eventActor)
  
  override protected def behavior = {
    // JDI event triggered when a class has been loaded
    case classPrepareEvent: ClassPrepareEvent =>
      debugTarget.stepFilters.anonFunctionsInRange(classPrepareEvent.referenceType, range).foreach(method => {
        val breakpoint = JdiRequestFactory.createMethodEntryBreakpoint(method, thread)
        requests += breakpoint
        debugTarget.eventDispatcher.setActorFor(this, breakpoint)
        breakpoint.enable()
      })
      reply(false)
    // JDI event triggered when a step has been performed
    case stepEvent: StepEvent =>
      reply(if (!debugTarget.stepFilters.isTransparentLocation(stepEvent.location)) {
        dispose()
        thread.suspendedFromScala(DebugEvent.STEP_OVER)
        true
      } else {
        false
      })
    // JDI event triggered when a breakpoint is hit
    case breakpointEvent: BreakpointEvent =>
      dispose()
      thread.suspendedFromScala(DebugEvent.STEP_OVER)
      reply(true)
    // user step request
    case ScalaStep.Step => 
      step()
    // step is terminated
    case ScalaStep.Stop =>
      dispose()
  }

  private def step() {
    val eventDispatcher = debugTarget.eventDispatcher

    requests.foreach {
      request =>
        eventDispatcher.setActorFor(this, request)
        request.enable()
    }

    thread.resumeFromScala(scalaStep, DebugEvent.STEP_OVER)
  }

  private def dispose(): Unit = {
    poison()
    unlink(thread.eventActor)
    val eventDispatcher = debugTarget.eventDispatcher
    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

    for(request <- requests) {
      request.disable()
      eventDispatcher.unsetActorFor(request)
      eventRequestManager.deleteEventRequest(request)
    }
  }

  override protected def preExit(): Unit = dispose()
}