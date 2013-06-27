package scala.tools.eclipse.debug.async

import com.sun.jdi.ObjectReference
import com.sun.jdi.StackFrame
import scala.collection.immutable
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import scala.tools.eclipse.debug.BaseDebuggerActor
import com.sun.jdi.event._
import scala.collection.JavaConverters._
import scala.tools.eclipse.debug.model.JdiRequestFactory
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.ReferenceType
import com.sun.jdi.ThreadReference
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.debug.model.ScalaValue
import scala.tools.eclipse.util.Utils

/** Install breakpoints in key places and collect stack frames.
 *
 */
class RetainedStackManager(debugTarget: ScalaDebugTarget) extends HasLogger {

  private var stackFrames: Map[ObjectReference, AsyncStackTrace] = immutable.HashMap()

  private var futureApplyRequest: BreakpointRequest = null

  object actor extends BaseDebuggerActor {
    override protected def behavior = {
      // JDI event triggered when a class has been loaded
      case classPrepareEvent: ClassPrepareEvent =>
        val refType = classPrepareEvent.referenceType()
        // find the right app to install
        programPoints.find(_.className == refType.name()) foreach { installMethodBreakpoint(refType, _) }
        reply(false)

      // JDI event triggered when a breakpoint is hit
      case breakpointEvent: BreakpointEvent =>
        appHit(breakpointEvent.thread, breakpointEvent.request().getProperty("app").asInstanceOf[AsyncProgramPoint])
        reply(false) // don't suspend this thread
    }
  }

  private def appHit(thread: ThreadReference, app: AsyncProgramPoint) {
    val topFrame = thread.frame(0)
    val args = topFrame.getArgumentValues()
    logger.debug(s"$app hit: topFrame arguments: $args")

    val body = args.get(app.paramIdx)
    val frames = thread.frames().asScala.toList
    logger.debug(s"Added ${frames.size} stack frames in cache.")
    stackFrames += (body.asInstanceOf[ObjectReference]) -> mkStackTrace(frames)
  }

  private def mkStackTrace(frames: Seq[StackFrame]): AsyncStackTrace = fineDebugTimed("mkStackTrace") {
    import collection.JavaConverters._

    val asyncFrames = for (frame <- frames) yield {
      val names = frame.visibleVariables()
      val values = frame.getValues(names)
      val locals = values.asScala.map {
        case (lvar, lval) => AsyncLocalVariable(lvar.name(), ScalaValue(lval, debugTarget))(debugTarget)
      }
      val location = frame.location()
      AsyncStackFrame(locals.toSeq, Location(location.sourceName, location.declaringType.name, location.lineNumber))(debugTarget)
    }

    AsyncStackTrace(asyncFrames)
  }

  private def installMethodBreakpoint(tpe: ReferenceType, app: AsyncProgramPoint) {
    val method = tpe.allMethods().asScala.find(_.name().startsWith(app.methodName))
    method.foreach { meth =>
      val req = JdiRequestFactory.createMethodEntryBreakpoint(method.get, debugTarget)
      debugTarget.eventDispatcher.setActorFor(actor, req)
      req.putProperty("app", app)
      req.enable()

      logger.debug(s"Installed method breakpoint for ${method.get.declaringType()}.${method.get.name}")
    }
  }

  //  private val FutureImpl = "scala.concurrent.package$"

  private val programPoints = List(
    AsyncProgramPoint("scala.concurrent.package$", "future", 0),
    AsyncProgramPoint("scala.actors.InternalReplyReactor$class", "$bang", 1))

  /** Return the saved stackframes for the given future body (if any). */
  def getStackFrameForFuture(future: ObjectReference): Option[AsyncStackTrace] = {
    stackFrames.get(future)
  }

  def start() {
    actor.start()
    for {
      app @ AsyncProgramPoint(clazz, meth, _) <- programPoints
      refType = debugTarget.virtualMachine.classesByName(clazz).asScala
    } if (!refType.isEmpty)
      installMethodBreakpoint(refType(0), app)
    else
      // in case it's not been loaded yet
      debugTarget.cache.addClassPrepareEventListener(actor, clazz)
  }

  /** Evaluated `op' and log the time in ms it took to execute it.
   */
  def fineDebugTimed[A](name: String)(op: => A): A = {
    val start = System.nanoTime()
    val res = op
    val end = System.nanoTime()

    logger.debug("%s: \t %,3d ns".format(name, end - start))
    res
  }

}