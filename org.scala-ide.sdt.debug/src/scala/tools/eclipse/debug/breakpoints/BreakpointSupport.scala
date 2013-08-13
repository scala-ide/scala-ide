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
import scala.collection.JavaConverters._
import scala.reflect.Manifest
import scala.tools.eclipse.debug.evaluation.ValueBinding


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

  implicit class RichTypeHelper(x: Any) {
    val cls = x.getClass
    def ifTypeOf[T](implicit manifest: Manifest[T]): Option[T] =
      if (manifest.runtimeClass.isAssignableFrom(cls))
        Some(x.asInstanceOf[T])
      else None
  }

  private val conditionalState: Option[ConditionalBreakpointState] = breakpoint match {
    case jlb: IJavaLineBreakpoint => Some(new ConditionalBreakpointState(jlb))
    case _ => None
  }

  private class ConditionalBreakpointState(val breakpoint: IJavaLineBreakpoint) {
    var isCompiled = false

    val packageName = "scala.eclipse.debug.evaluation.conditional"

    // FIXME: verify that this will always be unique
    val className = s"${(Option(breakpoint.getTypeName()) getOrElse "")}_Expression_${breakpoint.getLineNumber()}"

    val fullName = s"$packageName.$className"

    val methodName = "eval"

    private def compile(bindings: Seq[ValueBinding], classpath: Seq[String], scalaProject: ScalaProject)(thread: ScalaThread) = {
      def isGenericType(typename: String) = typename.contains("[")
      def removeObjectSuffix(tpe: String) = if (tpe.endsWith("$")) tpe.substring(0, tpe.length - 1) else tpe
      if (!isCompiled) {
        val assistance = debugTarget.objectByName("scala.tools.eclipse.debug.debugged.ReplAssistance", true, thread)
        val params = bindings.map { binding =>
          val typename = {
            val value = ScalaEvaluationEngine.boxed(binding.value)(thread)
            val getClassNameResult = assistance.invokeMethod("getClassName", thread, value)
            val jdiTypename = getClassNameResult.asInstanceOf[ScalaStringReference].underlying.value()
            if (isGenericType(jdiTypename))
              binding.tpe getOrElse jdiTypename
            else jdiTypename
          }
          s"${binding.name}: ${(typename)}"
        }
        val code = breakpoint.getCondition()
        val compileVal =
          assistance.invokeMethod(
            "createEvalMethod", thread,
            ScalaValue(fullName, debugTarget),
            ScalaEvaluationEngine.createStringList(debugTarget, thread, params),
            ScalaValue(breakpoint.getCondition(), debugTarget)
          )
        for (compiled <- compileVal.underlying.ifTypeOf[BooleanValue]) {
          isCompiled = compiled.booleanValue()
        }
      }

      isCompiled
    }

    def eval(stackFrame: ScalaStackFrame, launchDelegate: ScalaLaunchDelegate): Option[ScalaValue] = {
      val thread = stackFrame.thread
      val bindings = ScalaEvaluationEngine.yieldStackFrameBindings(Option(stackFrame), launchDelegate.scalaProject)
      if(compile(bindings, launchDelegate.classpath, launchDelegate.scalaProject)(thread)) {
        val assistance = debugTarget.objectByName("scala.tools.eclipse.debug.debugged.ReplAssistance", true, thread)
        val values = bindings.map(vb => ScalaEvaluationEngine.boxed(vb.value)(thread))
        val params = ScalaEvaluationEngine.createAnyList(debugTarget, stackFrame.thread, values)
        val result = assistance.invokeMethod("invoke", thread, ScalaValue(fullName, debugTarget), params)
        Some(result)
      } else None
    }
  }

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
      breakpointShouldSuspend(event) match {
        case true =>
          breakpointHit(event.location, event.thread)
          reply(true)
        case false =>
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
    val beQuiet = true
    conditionalState match {
      case Some(cs) if cs.breakpoint.isConditionEnabled() && cs.breakpoint.supportsCondition() => scala.util.Try {
        val launch = debugTarget.getLaunch
        val result: Option[Boolean] =
          for {
            launchDelegate <- launch.getLaunchConfiguration().getPreferredDelegate(Set(launch.getLaunchMode()).asJava).getDelegate().ifTypeOf[ScalaLaunchDelegate]
            scalaThread <- debugTarget.findScalaThread(event.thread())
            stackFrames = scalaThread.threadRef.frames.asScala.map(ScalaStackFrame(scalaThread, _)).toList
            stackFrame <- stackFrames.headOption
            evalResult <- cs.eval(stackFrame, launchDelegate)
            primValue <- evalResult.ifTypeOf[ScalaPrimitiveValue]
            boolValue <- primValue.underlying.ifTypeOf[BooleanValue]
          } yield boolValue.value()
        result getOrElse false
      } match {
        case scala.util.Success(b) => b
        case scala.util.Failure(e: DebugException) => {
          val original = e.getCause()
          false
        }
        case scala.util.Failure(e) => {
          val st = e.getStackTrace
          false
        }
      }
      case _ => true
    }
  }
}
