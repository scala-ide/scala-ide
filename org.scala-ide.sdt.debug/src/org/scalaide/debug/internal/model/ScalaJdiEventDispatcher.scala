package org.scalaide.debug.internal.model

import scala.collection.JavaConverters.asScalaIteratorConverter

import org.scalaide.debug.internal.BaseDebuggerActor
import org.scalaide.debug.internal.PoisonPill
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.Suppress

import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.VMDeathEvent
import com.sun.jdi.event.VMDisconnectEvent
import com.sun.jdi.event.VMStartEvent
import com.sun.jdi.request.EventRequest

import ScalaJdiEventDispatcherActor.SetActorFor
import ScalaJdiEventDispatcherActor.UnsetActorFor

import org.scalaide.debug.internal.BaseDebuggerActor
import org.scalaide.debug.internal.PoisonPill
import org.scalaide.util.internal.Suppress
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.scalaide.debug.internal.JdiEventDispatcher
import org.scalaide.debug.internal.JdiEventReceiver
import org.scalaide.debug.internal.JdiEventReceiver
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Await
import java.util.concurrent.atomic.AtomicBoolean

object ScalaJdiEventDispatcher {
  def apply(virtualMachine: VirtualMachine, scalaDebugTargetActor: BaseDebuggerActor): ScalaJdiEventDispatcher = {
    val companionActor = ScalaJdiEventDispatcherActor(scalaDebugTargetActor)
    new ScalaJdiEventDispatcher(virtualMachine, companionActor)
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

class ScalaJdiEventDispatcher private (virtualMachine: VirtualMachine, protected[debug] val companionActor: Suppress.DeprecatedWarning.Actor)
    extends Runnable with HasLogger with JdiEventDispatcher {

  private val running = new AtomicBoolean(true)

  /** @deprecated use `loop()` */
  override def run(): Unit = {
    // the polling loop runs until the VM is disconnected, or it is told to stop.
    // The events which have been already read will still be processed by the actor.
    val eventQueue = virtualMachine.eventQueue
    while (running.get) {
      try {
        // use a timeout of 1s, so it cleanly terminates on shut down
        val eventSet = eventQueue.remove(1000)
        if (eventSet != null) {
          companionActor ! eventSet
        }
      } catch {
        case e: VMDisconnectedException =>
          // it is likely that we will see this exception before being able to
          // shutdown the loop after a VMDisconnectedEvent
          dispose()
        case e: Exception =>
          // it should not die from any exception. Just logging
          logger.error("Error in jdi event loop", e)
      }
    }
  }

  def loop(): Unit = {
    val eventQueue = virtualMachine.eventQueue
    import scala.concurrent.ExecutionContext.Implicits.global
    val colaborator = () => Future {
      val eventSet = eventQueue.remove(1000)
      if (eventSet != null) {
        companionActor ! eventSet
      }
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
    running.getAndSet(false)
    companionActor ! PoisonPill
  }

  /**
   * Register the actor as recipient of the call back for the given request
   * TODO: I think we should try to use JDI's mechanisms to associate the actor to the request:
   *       @see EventRequest.setProperty(k, v) and EventRequest.getProperty
   */
  def setActorFor(actor: Suppress.DeprecatedWarning.Actor, request: EventRequest): Unit = {
    companionActor ! ScalaJdiEventDispatcherActor.SetActorFor(actor, request)
  }

  override def register(eventReceiver: JdiEventReceiver, request: EventRequest): Unit = {
    companionActor.asInstanceOf[ScalaJdiEventDispatcherActor].register(eventReceiver, request)
  }

  /**
   * Remove the call back target for the given request
   */
  def unsetActorFor(request: EventRequest): Unit = {
    companionActor ! ScalaJdiEventDispatcherActor.UnsetActorFor(request)
  }

  override def unregister(request: EventRequest): Unit = {
    companionActor.asInstanceOf[ScalaJdiEventDispatcherActor].unregister(request)
  }
}

private[model] object ScalaJdiEventDispatcherActor {
  case class SetActorFor(actor: Suppress.DeprecatedWarning.Actor, request: EventRequest)
  case class UnsetActorFor(request: EventRequest)

  def apply(scalaDebugTargetActor: Suppress.DeprecatedWarning.Actor): ScalaJdiEventDispatcherActor = {
    val actor = new ScalaJdiEventDispatcherActor(scalaDebugTargetActor)
    actor.start()
    actor
  }
}

/**
 * Actor used to manage a Scala event dispatcher. It keeps track of the registered actors,
 * and dispatches the JDI events.
 * This class is thread safe. Instances are not to be created outside of the ScalaJdiEventDispatcher object.
 */
private class ScalaJdiEventDispatcherActor private (scalaDebugTargetActor: Suppress.DeprecatedWarning.Actor) extends BaseDebuggerActor {
  import ScalaJdiEventDispatcherActor._

  /** event request to actor map */
  private var eventActorMap = Map[EventRequest, Suppress.DeprecatedWarning.Actor]()
  private val eventReceiversMap: scala.collection.mutable.Map[EventRequest, JdiEventReceiver] = {
    import scala.collection.JavaConverters._
    new ConcurrentHashMap[EventRequest, JdiEventReceiver].asScala
  }

  private[model] def register(receiver: JdiEventReceiver, request: EventRequest): Unit =
    eventReceiversMap += (request -> receiver)

  private[model] def unregister(request: EventRequest): Unit =
    eventReceiversMap -= request

  override protected def postStart(): Unit = link(scalaDebugTargetActor)

  override protected def behavior = {
    case SetActorFor(actor, request) => eventActorMap += (request -> actor)
    case UnsetActorFor(request) => eventActorMap -= request
    case eventSet: EventSet => processEventSet(eventSet)
  }

  /**
   * go through the events of the EventSet, and forward them to the registered
   * actors.
   * Resume or not the stopped threads depending on actor's answers
   */
  private def processEventSet(eventSet: EventSet): Unit = {
    import scala.collection.JavaConverters._

    var futures = List[Future[Any]]()
    var newFutures = List[scala.concurrent.Future[Boolean]]()

    /* Cannot use the eventSet directly. The JDI specification says it should implement java.util.Set,
     * but the eclipse implementation doesn't.
     *
     * see eclipse bug #383625 */
    // forward each event to the interested actor
    eventSet.eventIterator.asScala.foreach {
      case event: VMStartEvent =>
        futures ::= (scalaDebugTargetActor !! event)
      case event: VMDisconnectEvent =>
        futures ::= (scalaDebugTargetActor !! event)
      case event: VMDeathEvent =>
        futures ::= (scalaDebugTargetActor !! event)
      case event =>
        /* TODO: I think we should try to use JDI's mechanisms to associate the actor to the request:
         *  @see EventRequest.setProperty(k, v) and EventRequest.getProperty */
        eventActorMap.get(event.request).foreach { interestedActor =>
          futures ::= (interestedActor !! event)
        }
        eventReceiversMap.get(event.request).foreach { receiver =>
          newFutures ::= receiver.handle(event)
        }
    }

    // Message sent upon completion of the `futures`. This message is used to modify `this` actor's behavior.
    object FutureComputed

    // Change the actor's behavior to wait for the `futures` to complete
    become {
      case FutureComputed => unbecome()
    }

    var staySuspended = false
    staySuspended |= {
      import scala.concurrent.ExecutionContext.Implicits._
      import scala.concurrent.duration._
      Await.result(scala.concurrent.Future.fold(newFutures)(staySuspended)(_ | _), 1 minute)
    }

    val it = futures.iterator
    loopWhile(it.hasNext) {
      val future = it.next
      //FIXME: If any of the actor sitting behind the future dies, it could prevent the `ScalaJdiEventDispatcherActor` to
      //       resume and effectively blocking the whole application! Unfortunately, it doesn't look like there is an easy
      //       fix (changing the future into synchronous calls doesn't look like a good idea, as it's hard to know in advance
      //       what would be the right timeout value to set). (ticket #1001311)
      future.inputChannel.react {
        case result: Boolean => staySuspended |= result
      }
    }.andThen {
      try if (!staySuspended) eventSet.resume()
      finally ScalaJdiEventDispatcherActor.this ! FutureComputed
    }
    // Warning: Any code inserted here is never executed (it is effectively dead/unreachable code), because the above
    //          `loopWhile` never returns normally (i.e., it always throws an exception!).
  }
}
