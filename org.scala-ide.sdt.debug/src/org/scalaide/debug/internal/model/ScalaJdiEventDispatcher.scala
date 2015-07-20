package org.scalaide.debug.internal.model

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import scala.annotation.implicitNotFound
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.JavaConverters.mapAsScalaConcurrentMapConverter
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

import org.scalaide.debug.internal.JdiEventDispatcher
import org.scalaide.debug.internal.JdiEventReceiver
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.Suppress

import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.VMDeathEvent
import com.sun.jdi.event.VMDisconnectEvent
import com.sun.jdi.event.VMStartEvent
import com.sun.jdi.request.EventRequest

object ScalaJdiEventDispatcher {
  def apply(virtualMachine: VirtualMachine, scalaDebugTarget: JdiEventReceiver): ScalaJdiEventDispatcher = {
    val subordinate = ScalaJdiEventDispatcherSubordinate(scalaDebugTarget)
    new ScalaJdiEventDispatcher(virtualMachine, subordinate)
  }
}

case object Dispatch {
  def done = Future.successful {}

  def apply(runningCondition: => Boolean)(colaborator: () => Future[Unit])(implicit ec: ExecutionContext): Future[Unit] =
    if (runningCondition) {
      colaborator() flatMap { _ => apply(runningCondition)(colaborator) }
    } else done
}

/**
 * Actor based system pulling event from the vm event queue, and dispatching
 * them to the registered actors.
 * This class is thread safe. Instances have be created through its companion object.
 */

class ScalaJdiEventDispatcher private (virtualMachine: VirtualMachine, protected[debug] val subordinate: ScalaJdiEventDispatcherSubordinate)
    extends Runnable with HasLogger with JdiEventDispatcher {

  private val running = new AtomicBoolean(true)

  override def run(): Unit = {
    val eventQueue = virtualMachine.eventQueue
    import scala.concurrent.ExecutionContext.Implicits.global
    val colaborator = () => Future {
      Option(eventQueue.remove(1000))
    }.flatMap { events =>
      if (events.nonEmpty)
        subordinate.processEvent(events.get)
      else
        Dispatch.done
    } recoverWith {
      case e: VMDisconnectedException =>
        // it is likely that we will see this exception before being able to
        // shutdown the loop after a VMDisconnectedEvent
        dispose()
        Dispatch.done
      case e: Exception =>
        // it should not die from any exception. Just logging
        logger.error("Error in jdi event loop", e)
        Dispatch.done
    }
    Dispatch(running.get)(colaborator)
  }

  /**
   * release all resources
   */
  private[model] def dispose(): Unit = {
    if (running.getAndSet(false))
      subordinate.dispose()
  }

  private[model] def isRunning: Boolean = running.get

  /**
   * Register the actor as recipient of the call back for the given request
   * TODO: I think we should try to use JDI's mechanisms to associate the actor to the request:
   *       @see EventRequest.setProperty(k, v) and EventRequest.getProperty
   */
  override def register(eventReceiver: JdiEventReceiver, request: EventRequest): Unit =
    subordinate.register(eventReceiver, request)

  /**
   * Remove the call back target for the given request
   */
  override def unregister(request: EventRequest): Unit =
    subordinate.unregister(request)
}

private[model] object ScalaJdiEventDispatcherSubordinate {
  def apply(scalaDebugTarget: JdiEventReceiver): ScalaJdiEventDispatcherSubordinate =
    new ScalaJdiEventDispatcherSubordinate(scalaDebugTarget)
}

/**
 * Actor used to manage a Scala event dispatcher. It keeps track of the registered actors,
 * and dispatches the JDI events.
 * This class is thread safe. Instances are not to be created outside of the ScalaJdiEventDispatcher object.
 */
private[model] class ScalaJdiEventDispatcherSubordinate private (scalaDebugTarget: JdiEventReceiver) {
  import scala.concurrent.ExecutionContext.Implicits.global

  /** event request to receiver map */
  private val eventReceiversMap: scala.collection.mutable.Map[EventRequest, JdiEventReceiver] = {
    import scala.collection.JavaConverters._
    new ConcurrentHashMap[EventRequest, JdiEventReceiver].asScala
  }

  private[model] def register(receiver: JdiEventReceiver, request: EventRequest): Future[Unit] =
    Future(eventReceiversMap += (request -> receiver))

  private[model] def unregister(request: EventRequest): Future[Unit] =
    Future(eventReceiversMap -= request)

  private[model] def processEvent(eventSet: EventSet): Future[Unit] =
    Future(processEventSet(eventSet))

  /**
   * go through the events of the EventSet, and forward them to the registered
   * actors.
   * Resume or not the stopped threads depending on actor's answers
   */
  private def processEventSet(eventSet: EventSet): Unit = {
    import scala.concurrent.ExecutionContext.Implicits._
    import scala.collection.JavaConverters._

    var staySuspendeds = List[Future[Boolean]]()

    /* Cannot use the eventSet directly. The JDI specification says it should implement java.util.Set,
     * but the eclipse implementation doesn't.
     *
     * see eclipse bug #383625 */
    // forward each event to the interested actor
    eventSet.eventIterator.asScala.foreach {
      case event @ (_: VMStartEvent | _: VMDisconnectEvent | _: VMDeathEvent) =>
        staySuspendeds ::= scalaDebugTarget.handle(event)
      case event =>
        /* TODO: I think we should try to use JDI's mechanisms to associate the actor to the request:
         *  @see EventRequest.setProperty(k, v) and EventRequest.getProperty */
        eventReceiversMap.get(event.request).foreach { receiver =>
          staySuspendeds ::= receiver.handle(event)
        }
    }

    Future.fold(staySuspendeds)(false)(_ | _).andThen {
      case Success(false) => eventSet.resume()
      case _ =>
    }
  }

  private[model] def dispose(): Future[Unit] = Future {
    eventReceiversMap.clear()
  }
}
