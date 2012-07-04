package scala.tools.eclipse.debug

import scala.actors.Actor
import scala.collection.JavaConverters.asScalaBufferConverter
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
import scala.tools.eclipse.debug.model.JdiRequestFactory

object BreakpointSupport {
  // Initialize a breakpoint support instance
  def apply(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget): BreakpointSupport= {
    val actor= new BreakpointSupportActor(breakpoint, debugTarget)
    actor.createInitialRequests
    actor.start
    new BreakpointSupport(actor)
  }
}

/**
 * Manage the requests for one platform breakpoint.
 */
class BreakpointSupport private (eventActor: BreakpointSupportActor) {
  
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
  val ATTRIBUTE_TYPE_NAME = "org.eclipse.jdt.debug.core.typeName"
}

private[debug] class BreakpointSupportActor(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget) extends Actor {
  import BreakpointSupportActor._
  
  /**
   * The event requests created to support the breakpoint
   */
  private var eventRequests = List[EventRequest]()
  
  /**
   * Create all the requests needed at the time the breakpoint is added.
   * This should be done synchronously before starting the actor
   */
  def createInitialRequests() {
    val virtualMachine = debugTarget.virtualMachine
        val eventDispatcher = debugTarget.eventDispatcher
        
        // class prepare requests for the type and its nested types
        eventRequests ::= JdiRequestFactory.createClassPrepareRequest(getTypeName, debugTarget)
        eventRequests ::= JdiRequestFactory.createClassPrepareRequest(getTypeName + "$*", debugTarget)
        
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
    
    // enable the requests
    eventRequests.foreach {
      eventRequest =>
      eventDispatcher.setActorFor(this, eventRequest)
      eventRequest.enable
    }
  }

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
          exit
      }
    }
  }

  /**
   * Remove all created requests for this breakpoint
   */
  private def dispose() {
    val eventDispatcher = debugTarget.eventDispatcher
    val eventRequestManager= debugTarget.virtualMachine.eventRequestManager
    
    eventRequests.foreach {
      request =>
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
    val breakpointRequest = createBreakpointRequest(referenceType)

    breakpointRequest.foreach {
      br =>
        eventRequests ::= br
        debugTarget.eventDispatcher.setActorFor(this, br)
        br.enable
    }
  }

  /**
   * On line breakpoint hit, set the thread as suspended
   */
  private def breakpointHit(location: Location, thread: ThreadReference) {
    debugTarget.threadSuspended(thread, DebugEvent.BREAKPOINT)
  }

  private def createBreakpointRequest(referenceType: ReferenceType): Option[BreakpointRequest] = {
    JdiRequestFactory.createBreakpointRequest(referenceType, getLineNumber, debugTarget)
  }
  
  // breakpoint attributes
  
  private def getTypeName(): String = {
    breakpoint.getMarker.getAttribute(ATTRIBUTE_TYPE_NAME, "")
  }

  private def getLineNumber(): Int = {
    breakpoint.getMarker.getAttribute(IMarker.LINE_NUMBER, -1)
  }
  
}
