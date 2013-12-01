package scala.tools.eclipse.debug.async

import scala.tools.eclipse.debug.BaseDebuggerActor
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import scala.tools.eclipse.debug.model.ScalaThread
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.BreakpointEvent
import scala.tools.eclipse.logging.HasLogger
import com.sun.jdi.ObjectReference
import com.sun.jdi.request.EventRequest
import scala.tools.eclipse.debug.model.JdiRequestFactory
import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.StepRequest

case class StepMessageOut(debugTarget: ScalaDebugTarget, thread: ScalaThread) extends HasLogger {

  private var watchedMessage: Option[ObjectReference] = None

  val programSends = List(
    AsyncProgramPoint("akka.actor.RepointableActorRef", "$bang", 0),
    AsyncProgramPoint("scala.actors.InternalReplyReactor$class", "$bang", 1))

  val programReceives = List(
    AsyncProgramPoint("akka.actor.ActorCell", "receiveMessage", 0))

  private var sendRequests = Set[EventRequest]()
  private var receiveRequests = Set[EventRequest]()
  private var stepRequests = Set[EventRequest]()
  private var steps = 0

  def step() {
    sendRequests = programSends.flatMap(Utility.installMethodBreakpoint(debugTarget, _, internalActor)).toSet
    receiveRequests = programReceives.flatMap(Utility.installMethodBreakpoint(debugTarget, _, internalActor)).toSet
    internalActor.start()
    // CLIENT_REQUEST seems to be the only event that correctly updates the UI
    thread.resumeFromScala(DebugEvent.CLIENT_REQUEST)
  }

  object internalActor extends BaseDebuggerActor {
    override protected def behavior = {
      case breakpointEvent: BreakpointEvent if sendRequests(breakpointEvent.request()) =>
        val app = breakpointEvent.request().getProperty("app").asInstanceOf[AsyncProgramPoint]
        val topFrame = breakpointEvent.thread().frame(0)
        val args = topFrame.getArgumentValues()
        logger.debug(s"message out intercepted: topFrame arguments: $args")
        watchedMessage = Option(args.get(app.paramIdx).asInstanceOf[ObjectReference])

        reply(false) // don't suspend this thread

      case breakpointEvent: BreakpointEvent if receiveRequests(breakpointEvent.request()) =>
        val app = breakpointEvent.request().getProperty("app").asInstanceOf[AsyncProgramPoint]
        val topFrame = breakpointEvent.thread().frame(0)
        val args = topFrame.getArgumentValues()
        logger.debug(s"receive intercepted: topFrame arguments: $args")
        val msg = Option(args.get(app.paramIdx).asInstanceOf[ObjectReference])
        if (watchedMessage == msg) {
          logger.debug("Intercepted a good receive!")

          val targetThread = debugTarget.getScalaThread(breakpointEvent.thread())
          targetThread foreach { thread =>
            val stepReq = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_INTO, thread)
            stepReq.enable()
            stepRequests = Set(stepReq)
            debugTarget.eventDispatcher.setActorFor(this, stepReq)
            steps = 0
          }
        }
        reply(false)

      case stepEvent: StepEvent if stepEvent.location().method().name().contains("applyOrElse") =>
        disable()
        poison()
        logger.debug(s"Suspending thread ${stepEvent.thread.name()}")
        // most likely the breakpoint was hit on a different thread than the one we started with, so we find it here
        debugTarget.getScalaThread(stepEvent.thread()).foreach(_.suspendedFromScala(DebugEvent.BREAKPOINT))
        reply(true) // suspend here!

      case stepEvent: StepEvent =>
        logger.debug(s"Step $steps in ${stepEvent.location().method().name()}")
        steps += 1
        reply(false) // resume VM
    }

    private def disable() {
      val eventDispatcher = debugTarget.eventDispatcher
      val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

      for (request <- sendRequests ++ receiveRequests ++ stepRequests) {
        request.disable()
        eventDispatcher.unsetActorFor(request)
        eventRequestManager.deleteEventRequest(request)
      }
    }
  }
}