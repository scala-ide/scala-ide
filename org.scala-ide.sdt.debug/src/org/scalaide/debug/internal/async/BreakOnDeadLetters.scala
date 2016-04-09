package org.scalaide.debug.internal.async

import java.util.concurrent.atomic.AtomicReference

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future

import org.eclipse.debug.core.DebugEvent
import org.eclipse.jface.util.PropertyChangeEvent
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.debug.internal.JdiEventReceiver
import org.scalaide.debug.internal.model.ClassPrepareListener
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.SWTUtils.fnToPropertyChangeListener

import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.Event
import com.sun.jdi.request.EventRequest

import BreakOnDeadLetters.PreferenceId

/**
 * Responsible for installing and removing the break on dead letters
 * functionality of the async debugger on top of `debugTarget`.
 */
class BreakOnDeadLetters(debugTarget: ScalaDebugTarget) extends HasLogger {
  import scala.concurrent.ExecutionContext.Implicits.global
  private val asyncPoint = AsyncProgramPoint("akka.actor.DeadLetterActorRef", "$bang", 0)
  private val eventRequestsRef: AtomicReference[Set[EventRequest]] = new AtomicReference(Set())
  private[async] def eventRequests = eventRequestsRef.get

  private[async] def createRequests() =
    if (debugTarget.virtualMachine.classesByName(asyncPoint.className).asScala.isEmpty) {
      debugTarget.cache.addClassPrepareEventListener(subordinate, asyncPoint.className)
    } else {
      disable()
      eventRequestsRef.getAndSet(AsyncUtils.installMethodBreakpoint(debugTarget, asyncPoint, subordinate).toSet)
      if (eventRequests.isEmpty)
        logger.error("Could not install dead letter breakpoints")
    }

  private def disable(): Unit = {
    val eventDispatcher = debugTarget.eventDispatcher
    val eventRequestManager = debugTarget.virtualMachine.eventRequestManager
    for (request <- eventRequestsRef.getAndSet(Set())) {
      request.disable()
      eventDispatcher.unregister(request)
      eventRequestManager.deleteEventRequest(request)
    }
  }

  def start(): Unit = {
    ScalaPlugin().getPreferenceStore.addPropertyChangeListener(propListener)
    val enabled = ScalaPlugin().getPreferenceStore.getBoolean(PreferenceId)
    if (enabled)
      createRequests()
  }

  def dispose(): Unit = Future {
    ScalaPlugin().getPreferenceStore.removePropertyChangeListener(propListener)
    disable()
  }

  val propListener: PropertyChangeEvent => Unit = event => {
    if (event.getProperty() == PreferenceId) Future {
      if (event.getNewValue().asInstanceOf[Boolean])
        createRequests()
      else
        disable()
    }
  }

  object subordinate extends JdiEventReceiver with ClassPrepareListener {
    override protected def innerHandle: PartialFunction[Event, StaySuspended] = {
      case breakpointEvent: BreakpointEvent if eventRequests(breakpointEvent.request()) =>
        logger.debug(s"Suspending thread ${breakpointEvent.thread.name()}")
        // most likely the breakpoint was hit on a different thread than the one we started with, so we find it here
        debugTarget.getScalaThread(breakpointEvent.thread()).foreach(_.suspendedFromScala(DebugEvent.BREAKPOINT))
        true
    }

    override def notify(classPrepareEvent: ClassPrepareEvent): Future[Unit] = Future {
      val refType = classPrepareEvent.referenceType()
      if (asyncPoint.className == refType.name())
        createRequests()
    }
  }
}

object BreakOnDeadLetters {
  case object Shutdown
  case class PrefChange(event: PropertyChangeEvent)

  final val PreferenceId = "org.scalaide.debug.breakOnDeadLetters"
}
