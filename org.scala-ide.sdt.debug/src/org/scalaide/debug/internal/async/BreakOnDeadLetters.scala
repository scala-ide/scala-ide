package org.scalaide.debug.internal.async

import java.util.concurrent.atomic.AtomicReference

import scala.collection.JavaConverters.asScalaBufferConverter

import org.eclipse.debug.core.DebugEvent
import org.eclipse.jface.util.PropertyChangeEvent
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.debug.internal.BaseDebuggerActor
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.SWTUtils.fnToPropertyChangeListener

import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.request.EventRequest

import BreakOnDeadLetters.PrefChange
import BreakOnDeadLetters.PreferenceId
import BreakOnDeadLetters.Shutdown

/**
 * Responsible for installing and removing the break on dead letters
 * functionality of the async debugger on top of `debugTarget`.
 */
class BreakOnDeadLetters(debugTarget: ScalaDebugTarget) extends HasLogger {
  private val asyncPoint = AsyncProgramPoint("akka.actor.DeadLetterActorRef", "$bang", 0)
  private val eventRequestsRef: AtomicReference[Set[EventRequest]] = new AtomicReference(Set())
  private[async] def eventRequests = eventRequestsRef.get

  private[async] def createRequests() =
    if (debugTarget.virtualMachine.classesByName(asyncPoint.className).asScala.isEmpty) {
      debugTarget.cache.addClassPrepareEventListener(internalActor, asyncPoint.className)
    } else {
      disable()
      eventRequestsRef.getAndSet(AsyncUtils.installMethodBreakpoint(debugTarget, asyncPoint, internalActor).toSet)
      if (eventRequests.isEmpty)
        logger.error("Could not install dead letter breakpoints")
    }

  private def disable(): Unit = {
    val eventDispatcher = debugTarget.eventDispatcher
    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager
    for (request <- eventRequestsRef.getAndSet(Set())) {
      request.disable()
      eventDispatcher.unsetActorFor(request)
      eventRequestManager.deleteEventRequest(request)
    }
  }

  def start(): Unit = {
    internalActor.start()
    ScalaPlugin().getPreferenceStore.addPropertyChangeListener(propListener)
    val enabled = ScalaPlugin().getPreferenceStore.getBoolean(PreferenceId)
    if (enabled)
      createRequests()
  }

  def dispose(): Unit = {
    internalActor ! Shutdown
  }

  val propListener: PropertyChangeEvent ⇒ Unit = event ⇒ {
    if (event.getProperty() == PreferenceId)
      internalActor ! PrefChange(event)
  }

  object internalActor extends BaseDebuggerActor {
    override def behavior = {
      case PrefChange(event: PropertyChangeEvent) =>
        if (event.getNewValue().asInstanceOf[Boolean])
          createRequests()
        else
          disable()
      case Shutdown =>
        ScalaPlugin().getPreferenceStore.removePropertyChangeListener(propListener)
        disable()
        poison()
      case breakpointEvent: BreakpointEvent if eventRequests(breakpointEvent.request()) =>
        logger.debug(s"Suspending thread ${breakpointEvent.thread.name()}")
        // most likely the breakpoint was hit on a different thread than the one we started with, so we find it here
        debugTarget.getScalaThread(breakpointEvent.thread()).foreach(_.suspendedFromScala(DebugEvent.BREAKPOINT))
        reply(true) // suspend here!
      case classPrepareEvent: ClassPrepareEvent =>
        val refType = classPrepareEvent.referenceType()
        if (asyncPoint.className == refType.name())
          createRequests()
        reply(false)
    }
  }
}

object BreakOnDeadLetters {
  case object Shutdown
  case class PrefChange(event: PropertyChangeEvent)

  final val PreferenceId = "org.scalaide.debug.breakOnDeadLetters"
}
