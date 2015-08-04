package org.scalaide.debug.internal.breakpoints

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

import scala.collection.JavaConverters.mapAsScalaConcurrentMapConverter
import scala.collection.Seq
import scala.collection.concurrent
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise

import org.eclipse.core.resources.IMarkerDelta
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.IBreakpointListener
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.jdt.internal.debug.core.breakpoints.JavaLineBreakpoint
import org.scalaide.debug.internal.model.ScalaDebugTarget

object ScalaDebugBreakpointManager {
  /**
   * A debug message used to know if the event request associated to the passed `breakpoint` is enabled.
   *  @note Use this for test purposes only!
   */
  case class GetBreakpointRequestState(breakpoint: IBreakpoint)

  def apply(debugTarget: ScalaDebugTarget): ScalaDebugBreakpointManager = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val subordinate = new ScalaDebugBreakpointSubordinate(debugTarget)
    new ScalaDebugBreakpointManager(subordinate)
  }
}

/**
 * Setup the initial breakpoints, and listen to breakpoint changes, for the given ScalaDebugTarget.
 *
 * @note All breakpoint-event related methods in this class are asynchronous, by delegating to the companion
 *       actor. This seems useless (listeners are run in their own thread) and makes things somewhat harder to test.
 *       Maybe we should remove the companion actor in this case.
 */
class ScalaDebugBreakpointManager private ( /*public field only for testing purposes */ val subordinate: ScalaDebugBreakpointSubordinate) extends IBreakpointListener {
  /**
   * Used to wait until all required `Future` messages have been processed.
   *
   * @note Use this for test purposes only!
   */
  private val waitForAllCurrentFutures: AtomicReference[Future[Unit]] = new AtomicReference(Future.successful {})

  private def ravelFutures[T](b: => Future[T])(implicit ec: ExecutionContext): Unit = {
    val p = Promise[Unit]
    b.onComplete { t => p.success {}; }
    waitForAllCurrentFutures.getAndSet(waitForAllCurrentFutures.get.flatMap { _ => p.future })
  }

  override def breakpointChanged(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = ravelFutures {
    subordinate.breakpointChanged(breakpoint, delta)
  }

  override def breakpointRemoved(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = ravelFutures {
    subordinate.breakpointRemoved(breakpoint)
  }

  override def breakpointAdded(breakpoint: IBreakpoint): Unit = ravelFutures {
    subordinate.breakpointAdded(breakpoint)
  }

  /**
   * Intended to ensure that we'll hit already defined and enabled breakpoints after performing hcr.
   *
   * @param changedClassesNames fully qualified names of types
   */
  def reenableBreakpointsInClasses(changedClassesNames: Seq[String]): Unit = {
    subordinate.reenableBreakpointsAfterHcr(changedClassesNames)
  }

  // ------------

  def init(): Unit = {
    subordinate.initialize()
    DebugPlugin.getDefault.getBreakpointManager.addBreakpointListener(this)
  }

  def dispose(): Unit = {
    DebugPlugin.getDefault.getBreakpointManager.removeBreakpointListener(this)
    waitForAllCurrentFutures.getAndSet(Future.successful {})
    subordinate.exit()
  }

  /**
   * Wait for all `Future`s to be processed.
   *
   * @note Use this for test purposes only!
   */
  protected[debug] def waitForAllCurrentEvents(): Unit = {
    while (!waitForAllCurrentFutures.get.isCompleted) {}
  }

  /**
   * Check if the event request associated to the passed `breakpoint` is enabled/disabled.
   *
   *  @return None if the `breakpoint` isn't registered. Otherwise, the enabled state of the associated request is returned, wrapped in a `Some`.
   *  @note Use this for test purposes only!
   */
  protected[debug] def getBreakpointRequestState(breakpoint: IBreakpoint): Option[Boolean] =
    subordinate.breakpointRequestState(breakpoint)
}

private[debug] class ScalaDebugBreakpointSubordinate(debugTarget: ScalaDebugTarget)(implicit ec: ExecutionContext) {
  private final val JdtDebugUID = "org.eclipse.jdt.debug"

  import scala.collection._
  private val breakpoints: concurrent.Map[IBreakpoint, BreakpointSupportSubordinate] = {
    import scala.collection.JavaConverters._
    new ConcurrentHashMap[IBreakpoint, BreakpointSupportSubordinate].asScala
  }

  def breakpointChanged(breakpoint: IBreakpoint, delta: IMarkerDelta): Future[Unit] =
    breakpoints.get(breakpoint).map { breakpointSupport =>
      breakpointSupport.changed(delta)
    }.getOrElse(Future.successful {})

  def breakpointRemoved(breakpoint: IBreakpoint): Future[Unit] = Future {
    breakpoints.get(breakpoint).map { breakpointSupport =>
      breakpointSupport.exit()
      breakpoints -= breakpoint
    }
  }

  /**
   * There might be a situation when breakpoint is not found in map. This is only possible if the message was sent
   * between when the InitializeExistingBreakpoints message was sent and when the list of the current breakpoint
   * was fetched. Nothing to do, everything is already in the right state.
   */
  def breakpointAdded(breakpoint: IBreakpoint): Future[Unit] = Future {
    breakpoints.putIfAbsent(breakpoint, BreakpointSupport(breakpoint, debugTarget))
  }

  def initialize(): Unit = {
    def createBreakpointSupport(breakpoint: IBreakpoint): Unit = {
      breakpoints += (breakpoint -> BreakpointSupport(breakpoint, debugTarget))
    }

    DebugPlugin.getDefault.getBreakpointManager.getBreakpoints(JdtDebugUID).foreach(createBreakpointSupport)
  }

  private[debug] def breakpointRequestState(breakpoint: IBreakpoint): Option[Boolean] = {
    breakpoints.get(breakpoint).flatMap { breakpointSupport =>
      Some(breakpointSupport.breakpointRequestState())
    }
  }

  def reenableBreakpointsAfterHcr(changedClassesNames: Seq[String]): Future[Unit] = Future {
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
      breakpoints(breakpoint).reenableBreakpointRequestsAfterHcr()
    }
  }

  def exit(): Future[Unit] = Future {
    breakpoints.values.foreach(_.exit())
    breakpoints.clear()
  }
}
