package scala.tools.eclipse.debug

import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.IBreakpointsListener
import com.sun.jdi.VirtualMachine
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.core.resources.IMarkerDelta
import scala.actors.Actor
import org.eclipse.debug.core.IBreakpointListener
import org.eclipse.jdt.internal.debug.core.breakpoints.JavaBreakpoint
import org.eclipse.core.resources.IMarker
import com.sun.jdi.request.ClassPrepareRequest
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.EventRequest
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.Location
import com.sun.jdi.ThreadReference
import org.eclipse.debug.core.DebugEvent

object ScalaDebugBreakpointManager {
  val JDT_DEBUG_UID = "org.eclipse.jdt.debug"

  val ATTRIBUTE_TYPE_NAME = "org.eclipse.jdt.debug.core.typeName"
}

// Actor messages
case object InitializeExistingBreakpoints
case class BreakpointAdded(breakpoint: IBreakpoint)
case class BreakpointRemoved(breakpoint: IBreakpoint)
case class BreakpointChanged(breakpoint: IBreakpoint)

/**
 * Manage the requests for one platform breakpoint.
 */
class BreakpointSupport(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget) {
  import ScalaDebugBreakpointManager._
  import IMarker._

  val eventActor = new Actor {
    start
    def act() {
      loop {
        react {
          case event: ClassPrepareEvent =>
            classPrepared(event.referenceType)
            reply(false)
          case event: BreakpointEvent =>
            breakpointHit(event.location, event.thread)
            reply(true)
          case ActorExit =>
            exit
        }
      }
    }
  }

  private var eventRequests = List[EventRequest]()

  /**
   * Create all the requests needed at the time the breakpoint is added
   */
  def init() {
    val virtualMachine = debugTarget.virtualMachine
    val eventDispatcher = debugTarget.eventDispatcher
    val eventRequestManager= virtualMachine.eventRequestManager
    
    // class prepare requests for the type and its nested types
    val classPrepareRequest = eventRequestManager.createClassPrepareRequest()
    eventRequests ::= classPrepareRequest
    classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL)
    classPrepareRequest.addClassFilter(getTypeName)
    val classPrepareRequestDollar = eventRequestManager.createClassPrepareRequest()
    eventRequests ::= classPrepareRequestDollar
    classPrepareRequestDollar.setSuspendPolicy(EventRequest.SUSPEND_ALL)
    classPrepareRequestDollar.addClassFilter(getTypeName + "$*")

    
    import scala.collection.JavaConverters._
    // if the type is already loaded, add the breakpoint requests
    val loadedClasses = virtualMachine.classesByName(getTypeName)
    loadedClasses.asScala.foreach {
      loadedClass =>
        val breakpointRequest = createBreakpointRequest(loadedClass)
        breakpointRequest.foreach {eventRequests ::= _}
        
        // TODO: might be more effective to do the filtering ourselves from 'allClasses'
        loadedClass.nestedTypes.asScala.foreach{
          createBreakpointRequest(_).foreach{eventRequests ::= _}
        }
    }

    eventRequests.foreach {
      eventRequest =>
        eventDispatcher.setActorFor(eventActor, eventRequest)
        eventRequest.enable
    }
  }

  /**
   * Remove all created requests for this breakpoint
   */
  def dispose() {
    val eventDispatcher = debugTarget.eventDispatcher
    val eventRequestManager= debugTarget.virtualMachine.eventRequestManager
    
    eventRequests.foreach {
      request =>
        eventRequestManager.deleteEventRequest(request)
        eventDispatcher.unsetActorFor(request)
    }
    eventActor ! ActorExit
  }

  def changed() {
    // TODO: see what can be changed
    //  - enabled/disabled state
  }

  /**
   * Create the line breakpoint on class prepare event
   */
  def classPrepared(referenceType: ReferenceType) {
    val breakpointRequest = createBreakpointRequest(referenceType)

    breakpointRequest.foreach {
      br =>
        eventRequests ::= br
        debugTarget.eventDispatcher.setActorFor(eventActor, br)
        br.enable
    }
  }

  /**
   * create a line breakpoint on the given type
   */
  def createBreakpointRequest(referenceType: ReferenceType): Option[BreakpointRequest] = {
    import scala.collection.JavaConverters._
    val lineNumber = getLineNumber
    val locations = JDIUtil.referenceTypeToLocations(referenceType)
    // TODO: is it possible to have the same line number in multiple locations? need test case
    val line = locations.find(_.lineNumber == lineNumber)
    line.map {
      l =>
        val breakpointRequest = debugTarget.virtualMachine.eventRequestManager.createBreakpointRequest(l)
        breakpointRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
        breakpointRequest
    }
  }

  /**
   * On line breakpoint hit, set the thread as suspended
   */
  def breakpointHit(location: Location, thread: ThreadReference) {
    debugTarget.threadSuspended(thread, DebugEvent.BREAKPOINT)
  }

  def getTypeName(): String = {
    breakpoint.getMarker.getAttribute(ATTRIBUTE_TYPE_NAME, "")
  }

  def getLineNumber(): Int = {
    breakpoint.getMarker.getAttribute(LINE_NUMBER, -1)
  }

}

/**
 * Setup the initial breakpoints, and listen to breakpoint changes, for the given ScalaDebugTarget
 */
class ScalaDebugBreakpointManager(debugTarget: ScalaDebugTarget) extends IBreakpointListener {
  import ScalaDebugBreakpointManager._

  // from org.eclipse.debug.core.IBreakpointsListener

  def breakpointChanged(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = {
    eventActor ! BreakpointChanged(breakpoint)
  }

  def breakpointRemoved(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = {
    eventActor ! BreakpointRemoved(breakpoint)
  }

  def breakpointAdded(breakpoint: IBreakpoint): Unit = {
    eventActor ! BreakpointAdded(breakpoint)
  }

  // ------------

  val eventActor = new Actor {
    start

    var breakpoints = Map[IBreakpoint, BreakpointSupport]()

    /**
     * process the breakpoint events
     */
    def act {
      loop {
        react {
          case InitializeExistingBreakpoints =>
            DebugPlugin.getDefault.getBreakpointManager.getBreakpoints(JDT_DEBUG_UID).foreach {
              createBreakpointSupport(_)
            }
            reply(None)
          case BreakpointAdded(breakpoint) =>
            breakpoints.get(breakpoint) match {
              case None =>
                createBreakpointSupport(breakpoint)
              case _ =>
              // This is only possible if the message was sent between when the InitializeExistingBreakpoints
              // message was sent and when the list of the current breakpoint was fetched.
              // Nothing to do, everything is already in the right state
            }
          case BreakpointRemoved(breakpoint) =>
            breakpoints.get(breakpoint) match {
              case Some(breakpointSupport) =>
                breakpointSupport.dispose()
                breakpoints -= breakpoint
              case _ =>
              // see previous comment
            }
          case BreakpointChanged(breakpoint) =>
            breakpoints.get(breakpoint) match {
              case Some(breakpointSupport) =>
                breakpointSupport.changed()
              case _ =>
              // see previous comment
            }
          case ActorExit =>
            // not cleaning the requests
            // the connection to the vm is closing or already closed at this point
            exit
          case ActorDebug =>
            reply(None)
        }
      }
    }

    private def createBreakpointSupport(breakpoint: org.eclipse.debug.core.model.IBreakpoint): Unit = {
      val breakpointSupport = new BreakpointSupport(breakpoint, debugTarget)
      breakpoints += (breakpoint -> breakpointSupport)
      breakpointSupport.init()
    }
  }

  def init() {
    val future = eventActor !! InitializeExistingBreakpoints
    DebugPlugin.getDefault.getBreakpointManager.addBreakpointListener(this)
    // need to wait for all existing breakpoint to be initialized before continuing, the caller will resume the VM
    future()
  }
  
  def dispose() {
    DebugPlugin.getDefault.getBreakpointManager.removeBreakpointListener(this)
    eventActor ! ActorExit
  }

}