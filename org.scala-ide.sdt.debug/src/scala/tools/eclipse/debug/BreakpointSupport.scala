package scala.tools.eclipse.debug

import scala.actors.Actor
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.debug.model.JdiRequestFactory
import scala.tools.eclipse.debug.model.ScalaDebugTarget

import org.eclipse.core.resources.IMarker
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.model.IBreakpoint

import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.ThreadReference
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.EventRequest

object BreakpointSupport {
  // Initialize a breakpoint support instance
  def apply(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): BreakpointSupport = {
    val actor = BreakpointSupportActor(breakpoint, debugTarget)
    new BreakpointSupport(actor)
  }
}

/**
 * Manage the requests for one platform breakpoint.
 */
class BreakpointSupport private (eventActor: Actor) {

  def changed() {
    eventActor ! BreakpointSupportActor.Changed
  }

  def dispose() {
    eventActor ! ActorExit
  }

}

private[debug] object BreakpointSupportActor {
  // specific events
  case object Changed

  // attribute constants
  private val AttributeTypeName = "org.eclipse.jdt.debug.core.typeName"

  def apply(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): Actor = {
    val eventRequests = createClassPrepareRequests(breakpoint, debugTarget)
    eventRequests appendAll createBreakpointsRequests(breakpoint, debugTarget)

    val actor = new BreakpointSupportActor(breakpoint, debugTarget, eventRequests)

    enableRequests(debugTarget, actor, eventRequests)
    actor.start
    actor
  }

  /**
   * Create event requests to tell the VM to notify us when a class (or any of its inner classes) that contain the `breakpoint` is loaded.
   *  This is needed top set the breakpoint when the class gets loaded (meaning that you don't know at this point if the class has already been loaded or not)
   */
  private def createClassPrepareRequests(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): ListBuffer[EventRequest] = {
    val requests = new ListBuffer[EventRequest]

    // class prepare requests for the type and its nested types
    requests append JdiRequestFactory.createClassPrepareRequest(getTypeName(breakpoint), debugTarget)
    requests append JdiRequestFactory.createClassPrepareRequest(getTypeName(breakpoint) + "$*", debugTarget) // this is important for closures/anon-classes

    requests
  }

  /** Create event requests to tell the VM to notify us when it reaches the line for the current `breakpoint` */
  private def createBreakpointsRequests(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): ListBuffer[EventRequest] = {
    val requests = new ListBuffer[EventRequest]
    val virtualMachine = debugTarget.virtualMachine

    import scala.collection.JavaConverters._
    // if the type is already loaded, add the breakpoint requests
    val loadedClasses = virtualMachine.classesByName(getTypeName(breakpoint))

    loadedClasses.asScala.foreach { loadedClass =>
      val breakpointRequest = createBreakpointRequest(breakpoint, debugTarget, loadedClass)
      breakpointRequest.foreach { requests append _ }

      // TODO: might be more effective to do the filtering ourselves from 'allClasses'
      loadedClass.nestedTypes.asScala.foreach {
        createBreakpointRequest(breakpoint, debugTarget, _).foreach { requests append _ }
      }
    }
    requests
  }

  private def getTypeName(breakpoint: IBreakpoint): String = {
    breakpoint.getMarker.getAttribute(AttributeTypeName, "")
  }

  private def getLineNumber(breakpoint: IBreakpoint): Int = {
    breakpoint.getMarker.getAttribute(IMarker.LINE_NUMBER, -1)
  }

  private def createBreakpointRequest(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget, referenceType: ReferenceType): Option[BreakpointRequest] = {
    JdiRequestFactory.createBreakpointRequest(referenceType, getLineNumber(breakpoint), debugTarget)
  }

  /**
   * Create all the requests needed at the time the breakpoint is added.
   *  This should be done synchronously before starting the actor
   */
  private def enableRequests(debugTarget: ScalaDebugTarget, actor: Actor, eventRequests: ListBuffer[EventRequest]): Unit = {
    val eventDispatcher = debugTarget.eventDispatcher
    // enable the requests
    eventRequests.foreach { eventRequest =>
      eventDispatcher.setActorFor(actor, eventRequest)
      eventRequest.enable()
    }
  }
}

private class BreakpointSupportActor private (breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget, eventRequests: ListBuffer[EventRequest]) extends Actor {
  import BreakpointSupportActor.{ Changed, createBreakpointRequest }

  // Manage the events
  override def act() {
    loop {
      react {
        case event: ClassPrepareEvent =>
          // JDI event triggered when a class is loaded
          classPrepared(event.referenceType)
          reply(false)
        case event: BreakpointEvent =>
          // JDI event triggered when a breakpoint is hit
          breakpointHit(event.location, event.thread)
          reply(true)
        case Changed =>
          changed()
        case ActorExit =>
          // disable and clear the requests
          dispose()
          exit()
      }
    }
  }

  /**
   * Remove all created requests for this breakpoint
   */
  private def dispose() {
    val eventDispatcher = debugTarget.eventDispatcher
    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

    eventRequests.foreach { request =>
      eventRequestManager.deleteEventRequest(request)
      eventDispatcher.unsetActorFor(request)
    }
  }

  private def changed() {
    // TODO: see what can be changed
    //  - enabled/disabled state
  }

  /**
   * Create the line breakpoint on class prepare event
   */
  private def classPrepared(referenceType: ReferenceType) {
    val breakpointRequest = createBreakpointRequest(breakpoint, debugTarget, referenceType)

    breakpointRequest.foreach { br =>
      eventRequests append br
      debugTarget.eventDispatcher.setActorFor(this, br)
      br.enable()
    }
  }

  /**
   * On line breakpoint hit, set the thread as suspended
   */
  private def breakpointHit(location: Location, thread: ThreadReference) {
    debugTarget.threadSuspended(thread, DebugEvent.BREAKPOINT)
  }
}
