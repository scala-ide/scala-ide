/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.breakpoints

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.eclipse.core.resources.IMarkerDelta
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.jdt.debug.core.IJavaBreakpoint
import org.scalaide.debug.BreakpointContext
import org.scalaide.debug.DebugContext
import org.scalaide.debug.JdiEventCommand
import org.scalaide.debug.NoCommand
import org.scalaide.debug.SuspendExecution
import org.scalaide.debug.internal.JdiEventDispatcher
import org.scalaide.debug.internal.JdiEventReceiver
import org.scalaide.debug.internal.extensions.EventHandlerMapping
import org.scalaide.debug.internal.model.ClassPrepareListener
import org.scalaide.debug.internal.model.JdiRequestFactory
import org.scalaide.debug.internal.model.ScalaDebugTarget

import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.ThreadReference
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.Event
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.EventRequest

import BreakpointSupportSubordinate.createBreakpointRequest
import BreakpointSupportSubordinate.handleEvent
import RichBreakpoint.richBreakpoint

private[debug] object BreakpointSupport {
  /** Attribute Type Name */
  final val ATTR_TYPE_NAME = "org.eclipse.jdt.debug.core.typeName"

  /**
   * Create the breakpoint support actor.
   *
   *  @note `BreakpointSupportActor` instances are created only by the `ScalaDebugBreakpointManagerActor`, hence
   *        any uncaught exception that may occur during initialization (i.e., in `BreakpointSupportActor.apply`)
   *        will be caught by the `ScalaDebugBreakpointManagerActor` default exceptions' handler.
   */
  def apply(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): BreakpointSupportSubordinate = {
    BreakpointSupportSubordinate(breakpoint, debugTarget)
  }
}

private object BreakpointSupportSubordinate {
  val eventHandlerMappings = EventHandlerMapping.mappings

  /**
   * Sends the event to all registered event handlers and returns their results.
   * All `NoCommand` result values are filtered out.
   */
  def handleEvent(event: Event, context: DebugContext): Set[JdiEventCommand] = {
    val handlerResults = eventHandlerMappings.iterator.flatMap(_.withInstance(_.handleEvent(event, context)))
    handlerResults.filter(_ != NoCommand).toSet
  }

  def apply(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): BreakpointSupportSubordinate = {
    val typeName = breakpoint.typeName

    val breakpointRequests = createBreakpointsRequests(breakpoint, typeName, debugTarget)

    val subordinate = new BreakpointSupportSubordinate(breakpoint, debugTarget, typeName, ListBuffer(breakpointRequests: _*))

    debugTarget.cache.addClassPrepareEventListener(subordinate, typeName)

    subordinate
  }

  /** Create event requests to tell the VM to notify us when it reaches the line for the current `breakpoint` */
  private def createBreakpointsRequests(breakpoint: IBreakpoint, typeName: String, debugTarget: ScalaDebugTarget): Seq[EventRequest] = {
    val requests = new ListBuffer[EventRequest]

    debugTarget.cache.getLoadedNestedTypes(typeName).foreach {
      createBreakpointRequest(breakpoint, debugTarget, _).foreach { requests append _ }
    }

    requests.toSeq
  }

  private def createBreakpointRequest(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget, referenceType: ReferenceType): Option[BreakpointRequest] = {
    val suspendPolicy = breakpoint match {
      case javaBreakPoint: IJavaBreakpoint if javaBreakPoint.getSuspendPolicy == IJavaBreakpoint.SUSPEND_THREAD =>
        EventRequest.SUSPEND_EVENT_THREAD
      case javaBreakPoint: IJavaBreakpoint if javaBreakPoint.getSuspendPolicy == IJavaBreakpoint.SUSPEND_VM =>
        EventRequest.SUSPEND_ALL
      case _ => //default suspend only current thread
        EventRequest.SUSPEND_EVENT_THREAD
    }

    JdiRequestFactory.createBreakpointRequest(referenceType, breakpoint.lineNumber, debugTarget, suspendPolicy)
  }
}

/**
 * This actor manages the given breakpoint and its corresponding VM requests. It receives messages from:
 *
 *  - the JDI event queue, when a breakpoint is hit
 *  - the platform, when a breakpoint is changed (for instance, disabled)
 */
class BreakpointSupportSubordinate private (
    breakpoint: IBreakpoint,
    debugTarget: ScalaDebugTarget,
    typeName: String,
    breakpointRequests: ListBuffer[EventRequest]) extends ClassPrepareListener with JdiEventReceiver {
  import BreakpointSupportSubordinate._
  import scala.concurrent.ExecutionContext.Implicits.global

  /** Return true if the state of the `breakpointRequests` associated to this breakpoint is (or, if not yet loaded, will be) enabled in the VM. */
  private val requestsEnabled: AtomicBoolean = new AtomicBoolean

  private val eventDispatcher: JdiEventDispatcher = debugTarget.eventDispatcher

  breakpointRequests.foreach(listenForBreakpointRequest)
  updateBreakpointRequestState(isEnabled)

  /** Returns true if the `breakpoint` is enabled and its state should indeed be considered. */
  private def isEnabled: Boolean = breakpoint.isEnabled() && DebugPlugin.getDefault().getBreakpointManager().isEnabled()

  /** Register `this` actor to receive all notifications from the `eventDispatcher` related to the passed `request`.*/
  private def listenForBreakpointRequest(request: EventRequest): Unit =
    eventDispatcher.register(this, request)

  private def updateBreakpointRequestState(enabled: Boolean): Unit = {
    breakpointRequests.foreach(_.setEnabled(enabled))
    requestsEnabled.getAndSet(enabled)
  }

  private def handleJdiEventCommands(cmds: Set[JdiEventCommand]): PartialFunction[Event, StaySuspended] = {
    case event: BreakpointEvent if cmds(SuspendExecution) ⇒
      // JDI event triggered when a breakpoint is hit
      breakpointHit(event.location, event.thread)
      true
  }

  private def defaultCommands(event: Event): JdiEventCommand = event match {
    case _: BreakpointEvent ⇒ SuspendExecution
  }

  override protected def innerHandle = {
    case event =>
      val context = BreakpointContext(breakpoint, debugTarget)
      val cmds = {
        val cmds = handleEvent(event, context)
        if (cmds.nonEmpty) cmds else Set(defaultCommands(event))
      }
      handleJdiEventCommands(cmds)(event)
  }

  def breakpointRequestState(): Boolean =
    requestsEnabled.get

  /**
   * Remove all created requests for this breakpoint
   */
  def exit(): Unit = {
    debugTarget.cache.removeClassPrepareEventListener(this, typeName)
    val eventDispatcher = debugTarget.eventDispatcher
    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager
    breakpointRequests.foreach { request =>
      eventRequestManager.deleteEventRequest(request)
      eventDispatcher.unregister(request)
    }
  }

  /**
   * React to changes in the breakpoint marker and enable/disable VM breakpoint requests accordingly.
   *
   *  @note ClassPrepare events are always enabled, since the breakpoint at the specified line
   *        can be installed *only* after/when the class is loaded, and that might happen while this
   *        breakpoint is disabled.
   */
  def changed(delta: IMarkerDelta): Future[Unit] = Future {
    if (isEnabled ^ requestsEnabled.get) updateBreakpointRequestState(isEnabled)
  }

  /**
   * Create the line breakpoint for the newly loaded class.
   */
  override def notify(event: ClassPrepareEvent): Future[Unit] = Future {
    val referenceType = event.referenceType
    val breakpointRequest = createBreakpointRequest(breakpoint, debugTarget, referenceType)
    breakpointRequest.foreach { br =>
      breakpointRequests append br
      listenForBreakpointRequest(br)
      br.setEnabled(requestsEnabled.get)
    }
  }

  /**
   * On line breakpoint hit, set the thread as suspended
   */
  private def breakpointHit(location: Location, thread: ThreadReference): Unit = {
    debugTarget.threadSuspended(thread, DebugEvent.BREAKPOINT)
  }

  /**
   * After hcr often we don't get events related to breakpoint requests.
   * Reenabling them seems to help in most of cases.
   */
  def reenableBreakpointRequestsAfterHcr(): Unit = {
    breakpointRequests.foreach { breakpointRequest =>
      if (breakpointRequest.isEnabled()) {
        breakpointRequest.disable()
        breakpointRequest.enable()
      }
    }
  }
}
