package scala.tools.eclipse.debug.command

import scala.Option.option2Iterable
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.debug.JDIUtil.methodToLines
import scala.tools.eclipse.debug.model.{ScalaThread, ScalaStackFrame, ScalaDebugTarget}

import org.eclipse.debug.core.DebugEvent
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget
import org.eclipse.jdt.internal.debug.core.IJDIEventListener

import com.sun.jdi.event.{StepEvent, EventSet, Event, ClassPrepareEvent, BreakpointEvent}
import com.sun.jdi.request.{StepRequest, EventRequest}
import com.sun.jdi.{ThreadReference, ReferenceType, Method}

import ScalaStepOver.{createMethodEntryBreakpoint, anonFunctionsInRange}

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

      val loadedAnonFunctionsInRange = location.method.declaringType.nestedTypes.asScala.flatMap(anonFunctionsInRange(_, range))

      // if we are in an anonymous function, add the method
      if (location.declaringType.name.contains("$$anonfun$")) {
        loadedAnonFunctionsInRange ++= scalaStackFrame.getScalaDebugTarget.findAnonFunction(location.declaringType)
      }

      requests ++= loadedAnonFunctionsInRange.map(createMethodEntryBreakpoint(_, scalaStackFrame.stackFrame.thread))

      new ScalaStepOver(scalaStackFrame.getScalaDebugTarget, range, scalaStackFrame.thread, requests)
    }

  }

  def createMethodEntryBreakpoint(method: Method, thread: ThreadReference) = {
    import scala.collection.JavaConverters._

    val breakpointRequest = thread.virtualMachine.eventRequestManager.createBreakpointRequest(method.location)
    breakpointRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
    breakpointRequest.addThreadFilter(thread)

    breakpointRequest
  }

  // TODO: use ScalaDebugTarget#findAnonFunction
  def anonFunctionsInRange(refType: ReferenceType, range: Range) = {
    import scala.collection.JavaConverters._
    val methods = refType.methods.asScala.filter(method =>
       method.name.startsWith("apply") && !method.isAbstract && range.contains(method.location.lineNumber))

    // TODO: using isBridge was not working with List[Int]. Should check if we can use it by default with some extra checks when it fails.
    //      methods.find(!_.isBridge)

    methods.size match {
      case 3 =>
        // method with primitive parameter
        methods.find(_.name.startsWith("apply$")).orElse({
          // method with primitive return type (with specialization in 2.10.0)
          methods.find(!_.signature.startsWith("(Ljava/lang/Object;)"))
        })
      case 2 =>
        methods.find(_.signature != "(Ljava/lang/Object;)Ljava/lang/Object;")
      case 1 =>
        methods.headOption
      case _ =>
        None
    }
  }
}

class ScalaStepOver(target: ScalaDebugTarget, range: Range, thread: ScalaThread, requests: ListBuffer[EventRequest]) extends IJDIEventListener with ScalaStep {
  import ScalaStepOver._

  // Members declared in org.eclipse.jdt.internal.debug.core.IJDIEventListener

  def eventSetComplete(event: Event, target: JDIDebugTarget, suspend: Boolean, eventSet: EventSet): Unit = {
    // nothing to do
  }

  def handleEvent(event: Event, javaTarget: JDIDebugTarget, suspendVote: Boolean, eventSet: EventSet): Boolean = {
    event match {
      case classPrepareEvent: ClassPrepareEvent =>
        anonFunctionsInRange(classPrepareEvent.referenceType, range).foreach(method => {
          val breakpoint = createMethodEntryBreakpoint(method, thread.thread)
          requests += breakpoint
          javaTarget.getEventDispatcher.addJDIEventListener(this, breakpoint)
          breakpoint.enable
        })
        true
      case stepEvent: StepEvent =>
        if (target.isValidLocation(stepEvent.location)) {
          stop
          thread.suspendedFromScala(DebugEvent.STEP_OVER)
          false
        } else {
          true
        }
      case breakpointEvent: BreakpointEvent =>
        stop
        thread.suspendedFromScala(DebugEvent.STEP_OVER)
        false
      case _ =>
        suspendVote
    }
  }

  // Members declared in scala.tools.eclipse.debug.command.ScalaStep

  def step() {
    val eventDispatcher = target.javaTarget.getEventDispatcher

    requests.foreach(breakpoint => {
      eventDispatcher.addJDIEventListener(this, breakpoint)
      breakpoint.enable
    })

    thread.resumedFromScala(DebugEvent.STEP_OVER)
    thread.thread.resume
  }

  def stop() {
    val eventDispatcher = target.javaTarget.getEventDispatcher

    val eventRequestManager = thread.thread.virtualMachine.eventRequestManager

    requests.foreach(breakpoint => {
      breakpoint.disable
      eventDispatcher.removeJDIEventListener(this, breakpoint)
      eventRequestManager.deleteEventRequest(breakpoint)
    })
  }

}