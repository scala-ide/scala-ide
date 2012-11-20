package scala.tools.eclipse.debug

import scala.tools.eclipse.debug.model.ScalaDebugTarget
import org.eclipse.core.resources.IMarkerDelta
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.IBreakpointListener
import org.eclipse.debug.core.model.IBreakpoint
import scala.actors.Actor

object ScalaDebugBreakpointManager {
  /** A debug message used to wait until all required messages have been processed. */
  object ActorDebug

  def apply(debugTarget: ScalaDebugTarget): ScalaDebugBreakpointManager = {
    val companionActor = ScalaDebugBreakpointManagerActor(debugTarget)
    new ScalaDebugBreakpointManager(companionActor)
  }
}

/**
 * Setup the initial breakpoints, and listen to breakpoint changes, for the given ScalaDebugTarget
 */
class ScalaDebugBreakpointManager private (/*public field only for testing purposes */val companionActor: Actor) extends IBreakpointListener {
  import ScalaDebugBreakpointManagerActor._

  // from org.eclipse.debug.core.IBreakpointsListener

  override def breakpointChanged(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = {
    companionActor ! BreakpointChanged(breakpoint)
  }

  override def breakpointRemoved(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = {
    companionActor ! BreakpointRemoved(breakpoint)
  }

  override def breakpointAdded(breakpoint: IBreakpoint): Unit = {
    companionActor ! BreakpointAdded(breakpoint)
  }

  // ------------

  def init() {
    // need to wait for all existing breakpoint to be initialized before continuing, the caller will resume the VM
    companionActor !? Initialize
    DebugPlugin.getDefault.getBreakpointManager.addBreakpointListener(this)
  }

  def dispose() {
    DebugPlugin.getDefault.getBreakpointManager.removeBreakpointListener(this)
    companionActor ! PoisonPill
  }
  
  /**
   * Test support method.
   * Wait for a dummy event to be processed, to indicate that all previous events
   * have been processed.
   */
  protected[debug] def waitForAllCurrentEvents() {
    companionActor !? ScalaDebugBreakpointManager.ActorDebug
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

private class ScalaDebugBreakpointManagerActor private(debugTarget: ScalaDebugTarget) extends BaseDebuggerActor {
  import ScalaDebugBreakpointManagerActor._

  private var breakpoints = Map[IBreakpoint, BreakpointSupport]()

  override protected def postStart(): Unit = link(debugTarget.companionActor)

  /**
   * process the breakpoint events
   */
  override protected def behavior = {
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
    case ScalaDebugBreakpointManager.ActorDebug =>
      reply(None)
  }

  private def createBreakpointSupport(breakpoint: IBreakpoint): Unit = {
    breakpoints += (breakpoint -> BreakpointSupport(breakpoint, debugTarget))
  }

  override protected def preExit(): Unit = breakpoints.values.foreach(_.dispose())
}