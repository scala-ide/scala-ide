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

private[debug] object BreakpointSupport {
  /** Attribute Type Name */
  final val ATTR_TYPE_NAME = "org.eclipse.jdt.debug.core.typeName"

  /** A boolean marker attribute that indicates whether the JDI requests
   *  corresponding to this breakpoint are enabled or disabled.
   */
  final val ATTR_VM_REQUESTS_ENABLED = "org.scala-ide.sdt.debug.breakpoint.vm_enabled"

  /** Create the breakpoint support actor. */
  def apply(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): Actor = {
    BreakpointSupportActor(breakpoint, debugTarget)
  }
}

private[debug] object BreakpointSupportActor {
  // specific events
  case class Changed(delta: IMarkerDelta)


  def apply(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): Actor = {
    val classPrepareRequests = createClassPrepareRequests(breakpoint, debugTarget)
    val breakpointRequests   = createBreakpointsRequests(breakpoint, debugTarget)

    val actor = new BreakpointSupportActor(breakpoint, debugTarget, classPrepareRequests, ListBuffer(breakpointRequests: _*))

    enableRequests(breakpoint, debugTarget, actor, classPrepareRequests ++ breakpointRequests)
    actor.start()
    actor
  }

  /**
   * Create event requests to tell the VM to notify us when a class (or any of its inner classes) that contain the `breakpoint` is loaded.
   *  This is needed to set the breakpoint when the class gets loaded (meaning that you don't know at this point if the class has already been loaded or not)
   */
  private def createClassPrepareRequests(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): Seq[EventRequest] = {
    val requests = new ListBuffer[EventRequest]

    // class prepare requests for the type and its nested types
    requests append JdiRequestFactory.createClassPrepareRequest(breakpoint.typeName, debugTarget)
    requests append JdiRequestFactory.createClassPrepareRequest(breakpoint.typeName + "$*", debugTarget) // this is important for closures/anon-classes

    requests.toSeq
  }

  /** Create event requests to tell the VM to notify us when it reaches the line for the current `breakpoint` */
  private def createBreakpointsRequests(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): Seq[EventRequest] = {
    val requests = new ListBuffer[EventRequest]
    val virtualMachine = debugTarget.virtualMachine

    import scala.collection.JavaConverters._
    // if the type is already loaded, add the breakpoint requests
    val loadedClasses = virtualMachine.classesByName(breakpoint.typeName)

    loadedClasses.asScala.foreach { loadedClass =>
      val breakpointRequest = createBreakpointRequest(breakpoint, debugTarget, loadedClass)
      breakpointRequest.foreach { requests append _ }

      // TODO: might be more effective to do the filtering ourselves from 'allClasses'
      loadedClass.nestedTypes.asScala.foreach {
        createBreakpointRequest(breakpoint, debugTarget, _).foreach { requests append _ }
      }
    }
    requests.toSeq
  }

  private def createBreakpointRequest(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget, referenceType: ReferenceType): Option[BreakpointRequest] = {
    JdiRequestFactory.createBreakpointRequest(referenceType, breakpoint.lineNumber, debugTarget)
  }

  /**
   * Create all the requests needed at the time the breakpoint is added.
   *  This should be done synchronously before starting the actor
   */
  private def enableRequests(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget, actor: Actor, eventRequests: Seq[EventRequest]): Unit = {
    val eventDispatcher = debugTarget.eventDispatcher
    // enable the requests
    eventRequests.foreach { eventRequest =>
      eventDispatcher.setActorFor(actor, eventRequest)
      val enablement = if (eventRequest.isInstanceOf[ClassPrepareRequest]) true else breakpoint.isEnabled()
      eventRequest.setEnabled(enablement)
    }
    breakpoint.setVmRequestEnabled(breakpoint.isEnabled())
  }
}

/**
 * This actor manages the given breakpoint and its corresponding VM requests. It receives messages from:
 *
 *  - the JDI event queue, when a breakpoint is hit
 *  - the platform, when a breakpoint is changed (for instance, disabled)
 */
private class BreakpointSupportActor private (breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget, classPrepareRequests: Seq[EventRequest], breakpointRequests: ListBuffer[EventRequest]) extends BaseDebuggerActor {
  import BreakpointSupportActor.{ Changed, createBreakpointRequest }

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
  }

  /**
   * Remove all created requests for this breakpoint
   */
  override protected def preExit() {
    val eventDispatcher = debugTarget.eventDispatcher
    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

    (classPrepareRequests ++ breakpointRequests).foreach { request =>
      eventRequestManager.deleteEventRequest(request)
      eventDispatcher.unsetActorFor(request)
    }
  }

  /** Enable/disable VM breakpoint requests.
   *
   *  @note ClassPrepare events are always enabled, since the breakpoint at the specified line
   *        can be installed *only* after/when the class is loaded, and that might happen while this
   *        breakpoint is disabled.
   */
  private def changed(delta: IMarkerDelta) {
    if (breakpoint.isEnabled()) {
      if (!breakpoint.vmRequestEnabled){
        breakpointRequests foreach { _.enable() }
        logger.info("enabled " + breakpointRequests)
      }
    } else if (breakpoint.vmRequestEnabled) {
      breakpointRequests foreach { _.disable() }
      logger.info("disabled " + breakpointRequests)
    }
    breakpoint.setVmRequestEnabled(breakpoint.isEnabled())
  }

  /** Create the line breakpoint for the newly loaded class.
   */
  private def classPrepared(referenceType: ReferenceType) {
    val breakpointRequest = createBreakpointRequest(breakpoint, debugTarget, referenceType)

    breakpointRequest.foreach { br =>
      breakpointRequests append br
      debugTarget.eventDispatcher.setActorFor(this, br)
      br.setEnabled(breakpoint.isEnabled())
    }
  }

  /**
   * On line breakpoint hit, set the thread as suspended
   */
  private def breakpointHit(location: Location, thread: ThreadReference) {
    debugTarget.threadSuspended(thread, DebugEvent.BREAKPOINT)
  }
}
