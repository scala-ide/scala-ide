package org.scalaide.debug.internal.breakpoints

import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.eclipse.core.resources.IMarkerDelta
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.IBreakpointListener
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.jdt.internal.debug.core.breakpoints.JavaLineBreakpoint
import scala.actors.Actor
import org.scalaide.debug.internal.BaseDebuggerActor
import org.scalaide.debug.internal.PoisonPill

object ScalaDebugBreakpointManager {
  /** A debug message used to wait until all required messages have been processed.
   *  @note Use this for test purposes only!
   */
  case object ActorDebug
  /** A debug message used to know if the event request associated to the passed `breakpoint` is enabled.
   *  @note Use this for test purposes only!
   */
  case class GetBreakpointRequestState(breakpoint: IBreakpoint)

  def apply(debugTarget: ScalaDebugTarget): ScalaDebugBreakpointManager = {
    val companionActor = ScalaDebugBreakpointManagerActor(debugTarget)
    new ScalaDebugBreakpointManager(companionActor)
  }
}

/**
 * Setup the initial breakpoints, and listen to breakpoint changes, for the given ScalaDebugTarget.
 *
 * @note All breakpoint-event related methods in this class are asynchronous, by delegating to the companion
 *       actor. This seems useless (listeners are run in their own thread) and makes things somewhat harder to test.
 *       Maybe we should remove the companion actor in this case.
 */
class ScalaDebugBreakpointManager private (/*public field only for testing purposes */val companionActor: Actor) extends IBreakpointListener {
  import ScalaDebugBreakpointManagerActor._

  // from org.eclipse.debug.core.IBreakpointsListener

  override def breakpointChanged(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = {
    companionActor ! BreakpointChanged(breakpoint, delta)
  }

  override def breakpointRemoved(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = {
    companionActor ! BreakpointRemoved(breakpoint)
  }

  override def breakpointAdded(breakpoint: IBreakpoint): Unit = {
    companionActor ! BreakpointAdded(breakpoint)
  }

  /**
   * Intended to ensure that we'll hit already defined and enabled breakpoints after performing hcr.
   * @param changedClassesNames fully qualified names of types
   */
  def reenableBreakpointsInClasses(changedClassesNames: Seq[String]): Unit =
    companionActor ! ReenableBreakpointsAfterHcr(changedClassesNames)

  // ------------

  def init() {
    // need to wait for all existing breakpoint to be initialized before continuing, the caller will resume the VM
    companionActor !? Initialize // FIXME: This could block forever
    DebugPlugin.getDefault.getBreakpointManager.addBreakpointListener(this)
  }

  def dispose() {
    DebugPlugin.getDefault.getBreakpointManager.removeBreakpointListener(this)
    companionActor ! PoisonPill
  }

  /**
   * Wait for a dummy event to be processed, to indicate that all previous events
   * have been processed.
   *
   * @note Use this for test purposes only!
   */
  protected[debug] def waitForAllCurrentEvents() {
    companionActor !? ScalaDebugBreakpointManager.ActorDebug
  }

  /** Check if the event request associated to the passed `breakpoint` is enabled/disabled.
   *
   *  @return None if the `breakpoint` isn't registered. Otherwise, the enabled state of the associated request is returned, wrapped in a `Some`.
   *  @note Use this for test purposes only!
   */
  protected[debug] def getBreakpointRequestState(breakpoint: IBreakpoint): Option[Boolean] =
    (companionActor !? ScalaDebugBreakpointManager.GetBreakpointRequestState(breakpoint)).asInstanceOf[Option[Boolean]]
}

private[debug] object ScalaDebugBreakpointManagerActor {
  // Actor messages
  case object Initialize
  case class BreakpointAdded(breakpoint: IBreakpoint)
  case class BreakpointRemoved(breakpoint: IBreakpoint)
  case class BreakpointChanged(breakpoint: IBreakpoint, delta: IMarkerDelta)

  /** The message used to reenable all breakpoints related to given classes. */
  case class ReenableBreakpointsAfterHcr(classNames: Seq[String])

  private final val JdtDebugUID = "org.eclipse.jdt.debug"

  def apply(debugTarget: ScalaDebugTarget): Actor = {
    val actor = new ScalaDebugBreakpointManagerActor(debugTarget)
    actor.start()
    actor
  }
}

private class ScalaDebugBreakpointManagerActor private(debugTarget: ScalaDebugTarget) extends BaseDebuggerActor {
  import ScalaDebugBreakpointManagerActor._
  import BreakpointSupportActor.Changed
  import BreakpointSupportActor.ReenableBreakpointAfterHcr

  private var breakpoints = Map[IBreakpoint, Actor]()

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
          breakpointSupport ! PoisonPill
          breakpoints -= breakpoint
        case _ =>
          // see previous comment
      }
    case BreakpointChanged(breakpoint, delta) =>
      breakpoints.get(breakpoint) match {
        case Some(breakpointSupport) =>
          breakpointSupport ! Changed(delta)
        case _ =>
          // see previous comment
      }
    case ReenableBreakpointsAfterHcr(changedClassesNames) =>
      reenableBreakpointAfterHcr(changedClassesNames)
    case ScalaDebugBreakpointManager.ActorDebug =>
      reply(None)

    case msg @ ScalaDebugBreakpointManager.GetBreakpointRequestState(breakpoint) =>
      breakpoints.get(breakpoint) match {
        case Some(breakpointSupport) =>
          reply(Some(breakpointSupport !? msg))
        case None => reply(None)
      }
  }

  private def createBreakpointSupport(breakpoint: IBreakpoint): Unit = {
    breakpoints += (breakpoint -> BreakpointSupport(breakpoint, debugTarget))
  }

  private def reenableBreakpointAfterHcr(changedClassesNames: Seq[String]): Unit = {
    /*
     * We need to prepare names of changed classes and these taken from breakpoints because
     * for some reasons they differ. We need to change them slightly as:
     *
     * Type names used in breakpoints have double intermediate dollars,
     * e.g. debug.Foo$$x$$Bar instead of debug.Foo$x$Bar, debug.Foo$$x$ instead of debug.Foo$x$.
     *
     * There are also anonymous types which really should have double dollars but anyway
     * breakpoints for such types have currently set type like
     * com.test.debug.Foo$$x$$Bar$java.lang.Object$java.lang.Object
     * instead of
     * debug.Foo$x$Bar$$anon$2$$anon$1
     */
    val anonTypePattern = """\$anon\$[1-9][0-9]*"""
    val namesToCompareWithOnesFromBreakpoints = changedClassesNames.map(_.replaceAll(anonTypePattern, "java.lang.Object"))
    def isChanged(typeName: String): Boolean =
      namesToCompareWithOnesFromBreakpoints.contains(typeName.replace("$$", "$"))

    val affectedBreakpoints = breakpoints.keys.collect {
      case bp: JavaLineBreakpoint if isChanged(bp.getTypeName) => bp
    }
    affectedBreakpoints.foreach { breakpoint =>
      breakpoints(breakpoint) ! ReenableBreakpointAfterHcr
    }
  }

  override protected def preExit(): Unit = breakpoints.values.foreach(_ ! PoisonPill)
}
