package scala.tools.eclipse.debug.breakpoints

import scala.actors.Actor
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.debug.BaseDebuggerActor
import scala.tools.eclipse.debug.model.JdiRequestFactory
import scala.tools.eclipse.debug.model.ScalaClassType
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import scala.tools.eclipse.debug.model.ScalaObjectReference
import scala.tools.eclipse.debug.model.ScalaThread
import scala.tools.eclipse.debug.model.ScalaValue
import org.eclipse.core.resources.IMarkerDelta
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint
import com.sun.jdi.ClassType
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.EventRequest
import RichBreakpoint.richBreakpoint
import scala.tools.eclipse.debug.model.ScalaArrayReference
import com.sun.jdi.ArrayReference
import scala.tools.eclipse.debug.model.ScalaPrimitiveValue
import com.sun.jdi.BooleanValue
import scala.tools.eclipse.debug.model.ScalaStringReference
import scala.tools.eclipse.debug.evaluation.ScalaEvaluationEngine
import scala.tools.eclipse.debug.model.ScalaStackFrame
import org.eclipse.debug.core.model.IVariable
import scala.tools.eclipse.launching.ScalaLaunchDelegate
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.debug.core.DebugException

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
      val suspended = event.thread().isSuspended()
      breakpointShouldSuspend(event) match {
        case true =>
          breakpointHit(event.location, event.thread)
          reply(true)
        case false =>
          event.thread().resume()
          reply(false)
      }
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

  private def breakpointShouldSuspend(event: BreakpointEvent): Boolean = {
    import scala.collection.JavaConverters._

    def bindStackFrame(scalaProject: ScalaProject, evalEngine: ScalaEvaluationEngine, stackFrame: Option[ScalaStackFrame]) {
      for {
        frame <- stackFrame
        variable <- frame.variables
        value = variable.getValue
      } evalEngine.bind(variable.getName, value) {
          ScalaEvaluationEngine.findType(variable.getName, frame.stackFrame.location(), scalaProject)
        }
    }

    breakpoint match {
      case cbp: IJavaLineBreakpoint if cbp.isConditionEnabled() && cbp.supportsCondition() => {
        scala.util.Try {
          val launch = debugTarget.getLaunch
          val launchDelegate = launch.getLaunchConfiguration().getPreferredDelegate(Set(launch.getLaunchMode()).asJava)
          val result = launchDelegate.getDelegate() match {
            case scalaLaunchDelegate: ScalaLaunchDelegate =>
              for {
                scalaThread <- debugTarget.findScalaThread(event.thread())
                evalEngine = new ScalaEvaluationEngine(scalaLaunchDelegate.classpath, debugTarget, scalaThread)
                stackFrames = scalaThread.threadRef.frames.asScala.map(ScalaStackFrame(scalaThread, _)).toList
                _ = bindStackFrame(scalaLaunchDelegate.scalaProject, evalEngine, stackFrames.headOption)
                eval <- evalEngine.evaluate(cbp.getCondition())
                boolValue <- ScalaEvaluationEngine.booleanValue(eval, scalaThread)
              } yield boolValue
            case _ => None
          }
          result getOrElse false
        } match {
          case scala.util.Success(r) =>
            println("success: " + r)
            r
          case scala.util.Failure(e: DebugException) =>
            val original = e.getCause()
            false
          case scala.util.Failure(e) =>
            val st = e.getStackTrace()
            println(e)
            false
        }
      }
      case _ => true
    }
  }
}
