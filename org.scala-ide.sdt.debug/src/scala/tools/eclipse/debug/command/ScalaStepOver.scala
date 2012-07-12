package scala.tools.eclipse.debug.command

import scala.Option.option2Iterable
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.debug.JDIUtil.methodToLines
import scala.tools.eclipse.debug.model.{ ScalaThread, ScalaStackFrame, ScalaDebugTarget }
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.{ StepEvent, EventSet, Event, ClassPrepareEvent, BreakpointEvent }
import com.sun.jdi.request.{ StepRequest, EventRequest }
import com.sun.jdi.{ ThreadReference, ReferenceType, Method }
import scala.actors.Actor
import com.sun.jdi.request.BreakpointRequest
import scala.tools.eclipse.debug.ActorExit

object ScalaStepOver {

  final val LINE_NUMBER_UNAVAILABLE = -1

  def apply(scalaStackFrame: ScalaStackFrame): ScalaStepOver = {

    // TODO : two step process is weird and might not be needed and dangerous
    import scala.collection.JavaConverters._

    val eventRequestManager = scalaStackFrame.stackFrame.virtualMachine.eventRequestManager
    val location = scalaStackFrame.stackFrame.location

    val stepOverRequest = eventRequestManager.createStepRequest(scalaStackFrame.stackFrame.thread, StepRequest.STEP_LINE, StepRequest.STEP_OVER)
    stepOverRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

    val requests = ListBuffer[EventRequest](stepOverRequest)

    if (location.lineNumber == LINE_NUMBER_UNAVAILABLE) {

      new ScalaStepOver(scalaStackFrame.getScalaDebugTarget, null, scalaStackFrame.thread, requests)

    } else {

      val classPrepareRequest = eventRequestManager.createClassPrepareRequest
      classPrepareRequest.addClassFilter(location.declaringType.name + "$*")
      classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

      requests += classPrepareRequest

      // find anonFunction in range
      val currentMethodLastLine = methodToLines(location.method).max

      val range = Range(location.lineNumber, (location.method.declaringType.methods.asScala.flatten(methodToLines(_)).filter(_ > currentMethodLastLine) :+ Int.MaxValue).min)

      // TODO: nestedTypes triggers a AllClasses request to the VM. Having the list of nested types managed and cached by the debug target should be more effective.
      val loadedAnonFunctionsInRange = location.method.declaringType.nestedTypes.asScala.flatMap(scalaStackFrame.getScalaDebugTarget.anonFunctionsInRange(_, range))

      // if we are in an anonymous function, add the method
      if (location.declaringType.name.contains("$$anonfun$")) {
        loadedAnonFunctionsInRange ++= scalaStackFrame.getScalaDebugTarget.findAnonFunction(location.declaringType)
      }

      requests ++= loadedAnonFunctionsInRange.map(createMethodEntryBreakpoint(_, scalaStackFrame.stackFrame.thread))

      new ScalaStepOver(scalaStackFrame.getScalaDebugTarget, range, scalaStackFrame.thread, requests)
    }

  }

  /**
   * Create a breakpoint on the first instruction of the method
   */
  def createMethodEntryBreakpoint(method: Method, thread: ThreadReference): BreakpointRequest = {
    import scala.collection.JavaConverters._

    val breakpointRequest = thread.virtualMachine.eventRequestManager.createBreakpointRequest(method.location)
    breakpointRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
    breakpointRequest.addThreadFilter(thread)

    breakpointRequest
  }

}

class ScalaStepOver(target: ScalaDebugTarget, range: Range, thread: ScalaThread, requests: ListBuffer[EventRequest]) extends ScalaStep {
  import ScalaStepOver._

  // Members declared in scala.tools.eclipse.debug.command.ScalaStep

  def step() {
    val eventDispatcher = target.eventDispatcher

    requests.foreach {
      request =>
        eventDispatcher.setActorFor(eventActor, request)
        request.enable
    }

    thread.resumedFromScala(DebugEvent.STEP_OVER)
    thread.thread.resume
  }

  def stop() {
    val eventDispatcher = target.eventDispatcher

    val eventRequestManager = thread.thread.virtualMachine.eventRequestManager

    requests.foreach {
      request =>
        request.disable
        eventDispatcher.unsetActorFor(request)
        eventRequestManager.deleteEventRequest(request)
    }
    
    eventActor ! ActorExit

  }

  // ----------------

  /**
   *  process the debug events
   */
  val eventActor = new Actor {
    start
    def act() {
      loop {
        react {
          case classPrepareEvent: ClassPrepareEvent =>
            thread.getScalaDebugTarget.anonFunctionsInRange(classPrepareEvent.referenceType, range).foreach(method => {
              val breakpoint = createMethodEntryBreakpoint(method, thread.thread)
              requests += breakpoint
              target.eventDispatcher.setActorFor(this, breakpoint)
              breakpoint.enable
            })
            reply(false)
          case stepEvent: StepEvent =>
            reply(if (target.isValidLocation(stepEvent.location)) {
              stop
              thread.suspendedFromScala(DebugEvent.STEP_OVER)
              true
            } else {
              false
            })
          case breakpointEvent: BreakpointEvent =>
            stop
            thread.suspendedFromScala(DebugEvent.STEP_OVER)
            reply(true)
          case ActorExit =>
            exit
        }
      }
    }
  }
}