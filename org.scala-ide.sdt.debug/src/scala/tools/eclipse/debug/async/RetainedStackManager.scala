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

/** Install breakpoints in key places and collect stack frames.
 *
 */
class RetainedStackManager(debugTarget: ScalaDebugTarget) extends HasLogger {

  private var stackFrames: Map[ObjectReference, List[StackFrame]] = immutable.HashMap()

  private var futureApplyRequest: BreakpointRequest = null

  object actor extends BaseDebuggerActor {
    override protected def behavior = {
      // JDI event triggered when a class has been loaded
      case classPrepareEvent: ClassPrepareEvent =>
        installMethodBreakpoint(classPrepareEvent.referenceType())
        reply(false)

      // JDI event triggered when a breakpoint is hit
      case breakpointEvent: BreakpointEvent =>
        futureCreated(breakpointEvent.thread)
        reply(false) // don't suspend this thread
    }
  }

  private def futureCreated(thread: ThreadReference) {
    val topFrame = thread.frame(0)
    val args = topFrame.getArgumentValues()
    logger.debug("futureCreated: topFrame arguments: " + args)

    val body = args.get(0)
    val frames = thread.frames().asScala.toList
    logger.debug(s"Added ${frames.size} stack frames in cache.")
    stackFrames += (body.asInstanceOf[ObjectReference]) -> frames
  }

  private def installMethodBreakpoint(tpe: ReferenceType) {
    val Future_future = tpe.allMethods().asScala.find(_.name().contains("future"))
    Future_future.foreach { meth =>
      val req = JdiRequestFactory.createMethodEntryBreakpoint(Future_future.get, debugTarget)
      debugTarget.eventDispatcher.setActorFor(actor, req)
      req.enable()

      logger.debug(s"Installed method breakpoint for ${Future_future.get.declaringType()}.${Future_future.get.name}")
    }
  }

  private val FutureImpl = "scala.concurrent.package$"

  /** Return the saved stackframes for the given future body (if any). */
  def getStackFrameForFuture(future: ObjectReference): Option[List[StackFrame]] = {
    stackFrames.get(future)
  }

  def start() {
    actor.start()
    for {
      future <- debugTarget.virtualMachine.classesByName(FutureImpl).asScala
    } installMethodBreakpoint(future)
    // in case it's not been loaded yet
    debugTarget.cache.addClassPrepareEventListener(actor, FutureImpl)
  }
}