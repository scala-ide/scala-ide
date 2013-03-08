package scala.tools.eclipse.debug.breakpoints

import scala.actors.Actor
import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.debug.model.JdiRequestFactory
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.model.IBreakpoint
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.ThreadReference
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.EventRequest
import org.eclipse.core.resources.IMarkerDelta
import RichBreakpoint._
import scala.util.control.Exception
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.request.InvalidRequestStateException
import com.sun.jdi.request.ClassPrepareRequest
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.request.InvalidRequestStateException
import scala.tools.eclipse.debug.BaseDebuggerActor
import scala.tools.eclipse.debug.model.ScalaDebugCache
import org.eclipse.debug.core.DebugPlugin

private[debug] object BreakpointSupport {
  /** Attribute Type Name */
  final val ATTR_TYPE_NAME = "org.eclipse.jdt.debug.core.typeName"

  /** Create the breakpoint support actor.
   *
   *  @note `BreakpointSupportActor` instances are created only by the `ScalaDebugBreakpointManagerActor`, hence
   *        any uncaught exception that may occur during initialization (i.e., in `BreakpointSupportActor.apply`)
   *        will be caught by the `ScalaDebugBreakpointManagerActor` default exceptions' handler.
   */
  def apply(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): Actor = {
    BreakpointSupportActor(breakpoint, debugTarget)
  }
}

private object BreakpointSupportActor {
  // specific events
  case class Changed(delta: IMarkerDelta)

  def apply(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): Actor = {
    val typeName= breakpoint.typeName

    val breakpointRequests = createBreakpointsRequests(breakpoint, typeName, debugTarget)

    val actor = new BreakpointSupportActor(breakpoint, debugTarget, typeName, ListBuffer(breakpointRequests: _*))

    debugTarget.cache.addClassPrepareEventListener(actor, typeName)

    actor.start()
    actor
  }

  /** Create event requests to tell the VM to notify us when it reaches the line for the current `breakpoint` */
  private def createBreakpointsRequests(breakpoint: IBreakpoint, typeName: String, debugTarget: ScalaDebugTarget): Seq[EventRequest] = {
    val requests = new ListBuffer[EventRequest]
    val virtualMachine = debugTarget.virtualMachine

    debugTarget.cache.getLoadedNestedTypes(typeName).foreach {
        createBreakpointRequest(breakpoint, debugTarget, _).foreach { requests append _ }
    }

    requests.toSeq
  }

  private def createBreakpointRequest(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget, referenceType: ReferenceType): Option[BreakpointRequest] = {
    JdiRequestFactory.createBreakpointRequest(referenceType, breakpoint.lineNumber, debugTarget)
  }
}

/**
 * This actor manages the given breakpoint and its corresponding VM requests. It receives messages from:
 *
 *  - the JDI event queue, when a breakpoint is hit
 *  - the platform, when a breakpoint is changed (for instance, disabled)
 */
private class BreakpointSupportActor private (
    breakpoint: IBreakpoint,
    debugTarget: ScalaDebugTarget,
    typeName: String,
    breakpointRequests: ListBuffer[EventRequest]) extends BaseDebuggerActor {
  import BreakpointSupportActor.Changed
  import BreakpointSupportActor.createBreakpointRequest

  /** Return true if the state of the `breakpointRequests` associated to this breakpoint is (or, if not yet loaded, will be) enabled in the VM. */
  private var requestsEnabled = false

  private val eventDispatcher = debugTarget.eventDispatcher

  override def postStart(): Unit =  {
    breakpointRequests.foreach(listenForBreakpointRequest)
    updateBreakpointRequestState(isEnabled)
  }

  /** Returns true if the `breakpoint` is enabled and its state should indeed be considered. */
  private def isEnabled: Boolean = breakpoint.isEnabled() && DebugPlugin.getDefault().getBreakpointManager().isEnabled()

  /** Register `this` actor to receive all notifications from the `eventDispatcher` related to the passed `request`.*/
  private def listenForBreakpointRequest(request: EventRequest): Unit =
    eventDispatcher.setActorFor(this, request)

  private def updateBreakpointRequestState(enabled: Boolean): Unit = {
    breakpointRequests.foreach (_.setEnabled(enabled))
    requestsEnabled = enabled
  }

  // Manage the events
  override protected def behavior: PartialFunction[Any, Unit] = {
    case event: ClassPrepareEvent =>
      // JDI event triggered when a class is loaded
      classPrepared(event.referenceType)
      reply(false)
    case event: BreakpointEvent =>
      // JDI event triggered when a breakpoint is hit
      breakpointHit(event.location, event.thread)
      reply(true)
    case Changed(delta) =>
      // triggered by the platform, when the breakpoint changed state
      changed(delta)
    case ScalaDebugBreakpointManager.ActorDebug =>
      reply(None)
    case ScalaDebugBreakpointManager.GetBreakpointRequestState(_) =>
      reply(requestsEnabled)
  }

  /**
   * Remove all created requests for this breakpoint
   */
  override protected def preExit() {
    val eventDispatcher = debugTarget.eventDispatcher
    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

    debugTarget.cache.removeClassPrepareEventListener(this, typeName)

    breakpointRequests.foreach { request =>
      eventRequestManager.deleteEventRequest(request)
      eventDispatcher.unsetActorFor(request)
    }
  }

  /** React to changes in the breakpoint marker and enable/disable VM breakpoint requests accordingly.
   *
   *  @note ClassPrepare events are always enabled, since the breakpoint at the specified line
   *        can be installed *only* after/when the class is loaded, and that might happen while this
   *        breakpoint is disabled.
   */
  private def changed(delta: IMarkerDelta) {
    if(isEnabled ^ requestsEnabled) updateBreakpointRequestState(isEnabled)
  }

  /** Create the line breakpoint for the newly loaded class.
   */
  private def classPrepared(referenceType: ReferenceType) {
    val breakpointRequest = createBreakpointRequest(breakpoint, debugTarget, referenceType)

    breakpointRequest.foreach { br =>
      breakpointRequests append br
      listenForBreakpointRequest(br)
      br.setEnabled(requestsEnabled)
    }
  }

  /**
   * On line breakpoint hit, set the thread as suspended
   */
  private def breakpointHit(location: Location, thread: ThreadReference) {
    debugTarget.threadSuspended(thread, DebugEvent.BREAKPOINT)
  }
}
