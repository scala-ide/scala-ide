package scala.tools.eclipse.debug

import scala.actors.Actor
import scala.tools.eclipse.debug.model.ScalaDebugTarget

import org.eclipse.core.resources.IMarkerDelta
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.IBreakpointListener
import org.eclipse.debug.core.model.IBreakpoint

object ScalaDebugBreakpointManager {

  def apply(debugTarget: ScalaDebugTarget): ScalaDebugBreakpointManager = {
    val eventActor = new ScalaDebugBreakpointManagerActor(debugTarget)
    eventActor.start
    new ScalaDebugBreakpointManager(eventActor)
  }
}

/**
 * Setup the initial breakpoints, and listen to breakpoint changes, for the given ScalaDebugTarget
 */
class ScalaDebugBreakpointManager private (eventActor: ScalaDebugBreakpointManagerActor) extends IBreakpointListener {
  import ScalaDebugBreakpointManager._
  import ScalaDebugBreakpointManagerActor._

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

  def init() {
    val future = eventActor !! Initialize
    DebugPlugin.getDefault.getBreakpointManager.addBreakpointListener(this)
    // need to wait for all existing breakpoint to be initialized before continuing, the caller will resume the VM
    future()
  }

  def dispose() {
    DebugPlugin.getDefault.getBreakpointManager.removeBreakpointListener(this)
    eventActor ! ActorExit
  }
  
  /**
   * Test support method.
   * Wait for a dummy event to be processed, to indicate that all previous events
   * have been processed.
   */
  protected[debug] def waitForAllCurrentEvents() {
    eventActor !? ActorDebug
  }

}

private[debug] object ScalaDebugBreakpointManagerActor {
  // Actor messages
  case object Initialize
  case class BreakpointAdded(breakpoint: IBreakpoint)
  case class BreakpointRemoved(breakpoint: IBreakpoint)
  case class BreakpointChanged(breakpoint: IBreakpoint)

  val JDT_DEBUG_UID = "org.eclipse.jdt.debug"
}

private[debug] class ScalaDebugBreakpointManagerActor(debugTarget: ScalaDebugTarget) extends Actor {
  import ScalaDebugBreakpointManagerActor._

  private var breakpoints = Map[IBreakpoint, BreakpointSupport]()

  /**
   * process the breakpoint events
   */
  def act {
    loop {
      react {
        case Initialize =>
          // Enable all existing breakpoints
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
    breakpoints += (breakpoint -> BreakpointSupport(breakpoint, debugTarget))
  }
}