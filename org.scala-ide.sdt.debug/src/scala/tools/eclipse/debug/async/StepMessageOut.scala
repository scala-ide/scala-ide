package scala.tools.eclipse.debug.async

import scala.tools.eclipse.debug.BaseDebuggerActor
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import scala.tools.eclipse.debug.model.ScalaThread
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.BreakpointEvent
import scala.tools.eclipse.logging.HasLogger

class StepMessageOut(debugTarget: ScalaDebugTarget, thread: ScalaThread) extends HasLogger {

  val programSends = List(
    AsyncProgramPoint("akka.actor.RepointableActorRef", "$bang", 0),
    AsyncProgramPoint("scala.actors.InternalReplyReactor$class", "$bang", 1))

  val programReceives = List(
    AsyncProgramPoint("akka.actor.ActorCell", "receiveMessage", 0))

  def step() {
    programSends.foreach(Utility.installMethodBreakpoint(debugTarget, _, internalActor))
    thread.resumeFromScala(DebugEvent.RESUME)
  }

  object internalActor extends BaseDebuggerActor {
    override protected def behavior = {
      case breakpointEvent: BreakpointEvent =>
        val app = breakpointEvent.request().getProperty("app").asInstanceOf[AsyncProgramPoint]
        val topFrame = breakpointEvent.thread().frame(0)
        val args = topFrame.getArgumentValues()
        logger.debug(s"message out intercepted: topFrame arguments: $args")
        val message = args.get(app.paramIdx)

        reply(false) // don't suspend this thread
    }
  }
}