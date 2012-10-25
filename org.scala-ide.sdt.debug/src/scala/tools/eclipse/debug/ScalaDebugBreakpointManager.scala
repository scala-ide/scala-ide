package scala.tools.eclipse.debug

import scala.actors.Actor
import scala.tools.eclipse.debug.model.ScalaDebugTarget

import org.eclipse.core.resources.IMarkerDelta
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.IBreakpointListener
import org.eclipse.debug.core.model.IBreakpoint

object ScalaDebugBreakpointManager {

  def apply(debugTarget: ScalaDebugTarget): ScalaDebugBreakpointManager = {
    val eventActor = ScalaDebugBreakpointManagerActor(debugTarget)
    new ScalaDebugBreakpointManager(eventActor)
  }
}

/**
 * Setup the initial breakpoints, and listen to breakpoint changes, for the given ScalaDebugTarget
 */
class ScalaDebugBreakpointManager private (eventActor: Actor) extends IBreakpointListener {
  import ScalaDebugBreakpointManagerActor._

  // from org.eclipse.debug.core.IBreakpointsListener

  override def breakpointChanged(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = {
    eventActor ! BreakpointChanged(breakpoint)
  }

  override def breakpointRemoved(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = {
    eventActor ! BreakpointRemoved(breakpoint)
  }

  override def breakpointAdded(breakpoint: IBreakpoint): Unit = {
    eventActor ! BreakpointAdded(breakpoint)
  }

  // ------------

  def init() {
    // need to wait for all existing breakpoint to be initialized before continuing, the caller will resume the VM
    eventActor !? Initialize
    DebugPlugin.getDefault.getBreakpointManager.addBreakpointListener(this)
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

  private final val JdtDebugUID = "org.eclipse.jdt.debug"
    
  def apply(debugTarget: ScalaDebugTarget): Actor = {
    val actor = new ScalaDebugBreakpointManagerActor(debugTarget)
    actor.start()
    actor
  }
}

private class ScalaDebugBreakpointManagerActor private(debugTarget: ScalaDebugTarget) extends Actor {
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
          DebugPlugin.getDefault.getBreakpointManager.getBreakpoints(JdtDebugUID).foreach(createBreakpointSupport)
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
          exit()
        case ActorDebug =>
          reply(None)
      }
    }
  }

  private def createBreakpointSupport(breakpoint: IBreakpoint): Unit = {
    breakpoints += (breakpoint -> BreakpointSupport(breakpoint, debugTarget))
  }
}