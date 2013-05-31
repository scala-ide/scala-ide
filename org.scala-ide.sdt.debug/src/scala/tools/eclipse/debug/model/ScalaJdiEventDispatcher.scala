package scala.tools.eclipse.debug.model

import scala.tools.eclipse.logging.HasLogger
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.VMDeathEvent
import com.sun.jdi.event.VMDisconnectEvent
import com.sun.jdi.event.VMStartEvent
import com.sun.jdi.request.EventRequest
import scala.tools.eclipse.debug.BaseDebuggerActor
import scala.tools.eclipse.debug.PoisonPill
import scala.actors.Actor

object ScalaJdiEventDispatcher {
  def apply(virtualMachine: VirtualMachine, scalaDebugTargetActor: BaseDebuggerActor): ScalaJdiEventDispatcher = {
    val companionActor = ScalaJdiEventDispatcherActor(scalaDebugTargetActor)
    new ScalaJdiEventDispatcher(virtualMachine, companionActor)
  }
}

/**
 * Actor based system pulling event from the vm event queue, and dispatching
 * them to the registered actors.
 * This class is thread safe. Instances have be created through its companion object.
 */

class ScalaJdiEventDispatcher private (virtualMachine: VirtualMachine, protected[debug] val companionActor: Actor) extends Runnable with HasLogger {

  @volatile
  private var running = true

  override def run() {
    // the polling loop runs until the VM is disconnected, or it is told to stop.
    // The events which have been already read will still be processed by the actor.
    val eventQueue = virtualMachine.eventQueue
    while (running) {
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

  /**
   * release all resources
   */
  private[model] def dispose() {
    running = false
    companionActor ! PoisonPill
  }

  /**
   * Register the actor as recipient of the call back for the given request
   * TODO: I think we should try to use JDI's mechanisms to associate the actor to the request:
   *       @see EventRequest.setProperty(k, v) and EventRequest.getProperty
   */
  def setActorFor(actor: Actor, request: EventRequest) {
    companionActor ! ScalaJdiEventDispatcherActor.SetActorFor(actor, request)
  }

  /**
   * Remove the call back target for the given request
   */
  def unsetActorFor(request: EventRequest) {
    companionActor ! ScalaJdiEventDispatcherActor.UnsetActorFor(request)
  }
}

private[model] object ScalaJdiEventDispatcherActor {
  case class SetActorFor(actor: Actor, request: EventRequest)
  case class UnsetActorFor(request: EventRequest)

  def apply(scalaDebugTargetActor: Actor): ScalaJdiEventDispatcherActor = {
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
private class ScalaJdiEventDispatcherActor private (scalaDebugTargetActor: Actor) extends BaseDebuggerActor {
  import ScalaJdiEventDispatcherActor._

  /** event request to actor map */
  private var eventActorMap = Map[EventRequest, Actor]()

  override protected def postStart(): Unit = link(scalaDebugTargetActor)

  override protected def behavior = {
    case SetActorFor(actor, request) => eventActorMap += (request -> actor)
    case UnsetActorFor(request)      => eventActorMap -= request
    case eventSet: EventSet          => processEventSet(eventSet)
  }

  /**
   * go through the events of the EventSet, and forward them to the registered
   * actors.
   * Resume or not the stopped threads depending on actor's answers
   */
  private def processEventSet(eventSet: EventSet): Unit = {
    import scala.collection.JavaConverters._

    var futures = List[Future[Any]]()

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
    }

    // Message sent upon completion of the `futures`. This message is used to modify `this` actor's behavior.
    object FutureComputed

    // Change the actor's behavior to wait for the `futures` to complete
    become { case FutureComputed => unbecome() }

    var staySuspended = false
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