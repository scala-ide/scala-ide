package org.scalaide.debug.internal.command

import java.util.concurrent.atomic.AtomicBoolean

import scala.Option.option2Iterable
import scala.Range
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.eclipse.debug.core.DebugEvent
import org.scalaide.debug.internal.JDIUtil.methodToLines
import org.scalaide.debug.internal.JdiEventReceiver
import org.scalaide.debug.internal.model.ClassPrepareListener
import org.scalaide.debug.internal.model.JdiRequestFactory
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.model.ScalaStackFrame
import org.scalaide.debug.internal.model.ScalaThread

import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.StepRequest

object ScalaStepOver {

  final val LINE_NUMBER_UNAVAILABLE = -1

  def apply(scalaStackFrame: ScalaStackFrame): ScalaStep = {

    import scala.collection.JavaConverters._

    val debugTarget = scalaStackFrame.getDebugTarget
    val location = scalaStackFrame.stackFrame.location
    val typeName = location.declaringType.name
    val stepOverRequest = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_OVER, scalaStackFrame.thread)
    val requests = ListBuffer[EventRequest](stepOverRequest)
    val subordinate = if (location.lineNumber == LINE_NUMBER_UNAVAILABLE) {
      new ScalaStepOverSubordinate(debugTarget, typeName, rangeOpt = None, scalaStackFrame.thread, requests)
    } else {
      // find anonFunction in range
      val currentMethodLastLine = methodToLines(location.method).max
      val range = Range(location.lineNumber, (location.method.declaringType.methods.asScala.flatten(methodToLines(_)).filter(_ > currentMethodLastLine) :+ Int.MaxValue).min)
      val nestedAnonFuncPrefix = if (typeName.last == '$') {
        typeName + "$anonfun$"
      } else {
        typeName + "$$anonfun$"
      }
      val loadedAnonFunctionsInRange = debugTarget.cache.getLoadedNestedTypes(typeName).filter(_.name().startsWith(nestedAnonFuncPrefix)).flatMap(debugTarget.cache.getAnonFunctionsInRange(_, range)).toBuffer
      // if we are in an anonymous function, add the method
      if (typeName.contains("$$anonfun$")) {
        loadedAnonFunctionsInRange ++= debugTarget.cache.getAnonFunction(location.declaringType)
      }
      requests ++= loadedAnonFunctionsInRange.map(JdiRequestFactory.createMethodEntryBreakpoint(_, scalaStackFrame.thread))

      new ScalaStepOverSubordinate(debugTarget, typeName, Some(range), scalaStackFrame.thread, requests)
    }
    subordinate.scalaStep
  }

}

/**
 * Actor used to manage a Scala step over. It keeps track of the request needed to perform this step.
 * This class is thread safe. Instances are not to be created outside of the ScalaStepOver object.
 */
private[command] class ScalaStepOverSubordinate(debugTarget: ScalaDebugTarget, typeName: String, rangeOpt: Option[Range], thread: ScalaThread, requests: ListBuffer[EventRequest])
    extends ScalaStep with JdiEventReceiver with ClassPrepareListener {
  import scala.concurrent.ExecutionContext.Implicits.global
  protected[command] def scalaStep: ScalaStep = this

  private val enabled = new AtomicBoolean

  override def notify(classPrepareEvent: ClassPrepareEvent): Future[Unit] = Future {
    // JDI event triggered when a class has been loaded
    for {
      range <- rangeOpt
      method <- debugTarget.cache.getAnonFunctionsInRange(classPrepareEvent.referenceType, range)
    } {
      val breakpoint = JdiRequestFactory.createMethodEntryBreakpoint(method, thread)
      requests += breakpoint
      debugTarget.eventDispatcher.register(this, breakpoint)
      breakpoint.enable()
    }
  }

  override protected def innerHandle = {
    // JDI event triggered when a step has been performed
    case stepEvent: StepEvent =>
      if (!debugTarget.cache.isTransparentLocation(stepEvent.location)) {
        disable()
        thread.suspendedFromScala(DebugEvent.STEP_OVER)
        true
      } else {
        false
      }
    // JDI event triggered when a breakpoint is hit
    case breakpointEvent: BreakpointEvent =>
      disable()
      thread.suspendedFromScala(DebugEvent.STEP_OVER)
      true
  }

  override def step(): Unit = Future {
    enable()
    thread.resumeFromScala(scalaStep, DebugEvent.STEP_OVER)
  }

  override def stop(): Unit = Future {
    disable()
  }

  private def enable(): Unit = {
    if (!enabled.getAndSet(true)) {
      val eventDispatcher = debugTarget.eventDispatcher
      debugTarget.cache.addClassPrepareEventListener(this, typeName)
      requests.foreach {
        request =>
          eventDispatcher.register(this, request)
          request.enable()
      }
    }
  }

  private def disable(): Unit = {
    if (enabled.getAndSet(false)) {
      val eventDispatcher = debugTarget.eventDispatcher
      val eventRequestManager = debugTarget.virtualMachine.eventRequestManager
      for (request <- requests) {
        request.disable()
        eventDispatcher.unregister(request)
        eventRequestManager.deleteEventRequest(request)
      }
      debugTarget.cache.removeClassPrepareEventListener(this, typeName)
    }
  }
}
