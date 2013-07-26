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
import com.sun.jdi.ByteValue
import com.sun.jdi.DoubleValue
import com.sun.jdi.CharValue
import com.sun.jdi.FloatValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.ShortValue
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile

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

    def box(primValue: ScalaPrimitiveValue)(implicit thread: ScalaThread): ScalaObjectReference = {
      def create[T <: AnyVal](typename: String, value: T) = {
        import scala.collection.JavaConverters._
        val classObject = debugTarget.classByName(typename, true, thread).asInstanceOf[ScalaClassType]
        val sig = ScalaEvaluationEngine.constructorJNISig(value)
        val constructor = classObject.classType.concreteMethodByName("<init>", sig)
        new ScalaObjectReference(classObject.classType.newInstance(thread.threadRef, constructor, List(primValue.underlying).asJava, ClassType.INVOKE_SINGLE_THREADED), debugTarget)
      }
      primValue.underlying match {
        case v: BooleanValue => create("java.lang.Boolean", v.value)
        case v: ByteValue => create("java.lang.Byte", v.value)
        case v: CharValue => create("java.lang.Char", v.value)
        case v: DoubleValue => create("java.lang.Double", v.value)
        case v: FloatValue => create("java.lang.Float", v.value)
        case v: IntegerValue => create("java.lang.Integer", v.value)
        case v: LongValue => create("java.lang.Long", v.value)
        case v: ShortValue => create("java.lang.Short", v.value)
      }
    }

    def bindStackFrame(scalaProject: ScalaProject, evalEngine: ScalaEvaluationEngine, stackFrame: Option[ScalaStackFrame]) {
//      val location = event.location()
//      val sourcePath = location.sourcePath()
//      scalaProject.doWithPresentationCompiler { pc =>
//        val path = scalaProject.allSourceFiles.find(_.toString().endsWith(sourcePath)).getOrElse(null)
//        val s = path.getFullPath().toString()
//        ScalaSourceFile.createFromPath(s) match {
//          case Some(sf: ScalaSourceFile) => {
//            val cu = sf.getCompilationUnit.asInstanceOf[ScalaCompilationUnit]
//            cu.withSourceFile { (src, compiler) =>
//              val children = cu.getChildren()
//              val s = children.mkString(",")
//            } ()
//          }
//          case x => {
//            val t = x
//            println(x)
//          }
//        }
//      }


      for {
        window <- ScalaPlugin.getWorkbenchWindow
        page <- window.getPages()
      }
      for {
        frame <- stackFrame
        variable <- frame.variables
        if variable.getValue.isInstanceOf[ScalaValue]
        value = variable.getValue.asInstanceOf[ScalaValue]
      } {
        val bindValue = value match {
          case primitive: ScalaPrimitiveValue => box(primitive)(evalEngine.thread)
          case _ => value
        }
        val assistance = evalEngine.target.objectByName("scala.tools.eclipse.debug.debugged.ReplAssistance", true, evalEngine.thread)
        val typename = assistance.invokeMethod("getClassName", evalEngine.thread, bindValue).asInstanceOf[ScalaStringReference].underlying.value()
        val name = if (variable.getName == "this") "this$" else variable.getName
        evalEngine.bind(name, typename, bindValue, Nil) // FIXME: properly get modifiers
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
          case scala.util.Success(r) => {
            println("success: " + r)
            r
          }
          case scala.util.Failure(e) => {
            val st = e.getStackTrace()
            println(e)
            false
          }
        }
      }
      case _ => true
    }
  }
}
