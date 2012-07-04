package scala.tools.eclipse.debug.command
import scala.Option.option2Iterable
import scala.actors.Actor
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.debug.ActorExit
import scala.tools.eclipse.debug.JDIUtil.methodToLines
import scala.tools.eclipse.debug.model.{ ScalaThread, ScalaStackFrame, ScalaDebugTarget }
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.{ ThreadReference, Method }
import com.sun.jdi.event.{ StepEvent, ClassPrepareEvent, BreakpointEvent }
import com.sun.jdi.request.{ StepRequest, EventRequest }
import com.sun.jdi.request.BreakpointRequest
import scala.tools.eclipse.debug.model.JdiRequestFactory

object ScalaStepOver {

  final val LINE_NUMBER_UNAVAILABLE = -1

  def apply(scalaStackFrame: ScalaStackFrame): ScalaStepOver = {

    import scala.collection.JavaConverters._

    val location = scalaStackFrame.stackFrame.location

    val stepOverRequest = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_OVER, scalaStackFrame.thread)

    val requests = ListBuffer[EventRequest](stepOverRequest)

    val actor = if (location.lineNumber == LINE_NUMBER_UNAVAILABLE) {

      new ScalaStepOverActor(scalaStackFrame.debugTarget, null, scalaStackFrame.thread, requests)

    } else {

      val classPrepareRequest = JdiRequestFactory.createClassPrepareRequest(location.declaringType.name + "$*", scalaStackFrame.debugTarget)

      requests += classPrepareRequest

      // find anonFunction in range
      val currentMethodLastLine = methodToLines(location.method).max

      val range = Range(location.lineNumber, (location.method.declaringType.methods.asScala.flatten(methodToLines(_)).filter(_ > currentMethodLastLine) :+ Int.MaxValue).min)

      // TODO: nestedTypes triggers a AllClasses request to the VM. Having the list of nested types managed and cached by the debug target should be more effective.
      val loadedAnonFunctionsInRange = location.method.declaringType.nestedTypes.asScala.flatMap(scalaStackFrame.debugTarget.anonFunctionsInRange(_, range))

      // if we are in an anonymous function, add the method
      if (location.declaringType.name.contains("$$anonfun$")) {
        loadedAnonFunctionsInRange ++= scalaStackFrame.debugTarget.findAnonFunction(location.declaringType)
      }

      requests ++= loadedAnonFunctionsInRange.map(JdiRequestFactory.createMethodEntryBreakpoint(_, scalaStackFrame.thread))

      new ScalaStepOverActor(scalaStackFrame.debugTarget, range, scalaStackFrame.thread, requests)
    }

    val step = new ScalaStepOver(actor)
    actor.start(step)
    step
  }

}

class ScalaStepOver private (eventActor: ScalaStepOverActor) extends ScalaStep {

  // Members declared in scala.tools.eclipse.debug.command.ScalaStep

  def step() {
    eventActor ! ScalaStep.Step
  }

  def stop() {
    eventActor ! ActorExit

  }

  // ----------------

}

/**
 *  process the debug events
 */
private[command] class ScalaStepOverActor(target: ScalaDebugTarget, range: Range, thread: ScalaThread, requests: ListBuffer[EventRequest]) extends Actor {

    
  private var scalaStep: ScalaStepOver = _
  
  def start(step: ScalaStepOver) {
    scalaStep= step
    start()
  }
  
  def act() {
    loop {
      react {
        // JDI event triggered when a class has been loaded
        case classPrepareEvent: ClassPrepareEvent =>
          thread.debugTarget.anonFunctionsInRange(classPrepareEvent.referenceType, range).foreach(method => {
            val breakpoint = JdiRequestFactory.createMethodEntryBreakpoint(method, thread)
            requests += breakpoint
            target.eventDispatcher.setActorFor(this, breakpoint)
            breakpoint.enable
          })
          reply(false)
        // JDI event triggered when a step has been performed
        case stepEvent: StepEvent =>
          reply(if (target.isValidLocation(stepEvent.location)) {
            dispose
            thread.suspendedFromScala(DebugEvent.STEP_OVER)
            true
          } else {
            false
          })
        // JDI event triggered when a breakpoint is hit
        case breakpointEvent: BreakpointEvent =>
          dispose
          thread.suspendedFromScala(DebugEvent.STEP_OVER)
          reply(true)
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
    val eventDispatcher = target.eventDispatcher

    requests.foreach {
      request =>
        eventDispatcher.setActorFor(this, request)
        request.enable
    }

    thread.resumeFromScala(scalaStep, DebugEvent.STEP_OVER)
  }

  private def dispose() {
    val eventDispatcher = target.eventDispatcher

    val eventRequestManager = target.virtualMachine.eventRequestManager

    requests.foreach {
      request =>
        request.disable
        eventDispatcher.unsetActorFor(request)
        eventRequestManager.deleteEventRequest(request)
    }
    
    this ! ActorExit
  }
}