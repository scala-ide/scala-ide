package org.scalaide.debug.internal.async

import org.scalaide.debug.internal.BaseDebuggerActor
import org.scalaide.core.ScalaPlugin
import org.scalaide.util.internal.eclipse.SWTUtils._
import org.eclipse.jface.util.PropertyChangeEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.event.BreakpointEvent
import org.eclipse.debug.core.DebugEvent
import org.scalaide.logging.HasLogger
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.PoisonPill

class BreakOnDeadLetters(debugTarget: ScalaDebugTarget) extends HasLogger {
  import BreakOnDeadLetters._

  def start() {
    internalActor.start()
    internalActor ! Initialize
  }

  def dispose() {
    internalActor ! Shutdown
  }

  def propListener(event: PropertyChangeEvent): Any = {
    if (event.getProperty() == preferenceID)
      internalActor ! PrefChange(event)
  }

  object internalActor extends BaseDebuggerActor {
    private val asyncPoint = AsyncProgramPoint("akka.actor.DeadLetterActorRef", "$bang", 0)
    private var eventRequests: Set[EventRequest] = Set()

    override def behavior = {

      case Initialize =>
        ScalaPlugin.prefStore.addPropertyChangeListener(propListener _)
        val enabled = ScalaPlugin.prefStore.getBoolean(preferenceID)
        if (enabled)
          createRequests()

      case PrefChange(event: PropertyChangeEvent) =>
        if (event.getNewValue().asInstanceOf[Boolean])
          createRequests()
        else
          disable()

      case Shutdown =>
        disable()
        poison()

      case breakpointEvent: BreakpointEvent if eventRequests(breakpointEvent.request()) =>
        val targetThread = debugTarget.getScalaThread(breakpointEvent.thread)
        //        disable()
        //        poison()
        logger.debug(s"Suspending thread ${breakpointEvent.thread.name()}")
        // most likely the breakpoint was hit on a different thread than the one we started with, so we find it here
        debugTarget.getScalaThread(breakpointEvent.thread()).foreach(_.suspendedFromScala(DebugEvent.BREAKPOINT))
        reply(true) // suspend here!
    }

    def createRequests() = {
      eventRequests = Utility.installMethodBreakpoint(debugTarget, asyncPoint, internalActor).toSet
      if (eventRequests.isEmpty)
        logger.error("Could not install dead letter breakpoints")
    }

    private def disable() {
      ScalaPlugin.prefStore.removePropertyChangeListener(propListener _)
      val eventDispatcher = debugTarget.eventDispatcher
      val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

      for (request <- eventRequests) {
        request.disable()
        eventDispatcher.unsetActorFor(request)
        eventRequestManager.deleteEventRequest(request)
      }
    }

  }
}

object BreakOnDeadLetters {
  case object Initialize
  case object Shutdown
  case class PrefChange(event: PropertyChangeEvent)

  final val preferenceID = "debug.breakOnDeadLetters"
}

