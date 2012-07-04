package scala.tools.eclipse.debug.model

import com.sun.jdi.VirtualMachine
import scala.actors.Actor
import com.sun.jdi.request.EventRequest
import scala.tools.eclipse.logging.HasLogger
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.Event
import scala.actors.Future
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.event.VMDisconnectEvent
import com.sun.jdi.event.VMDeathEvent
import com.sun.jdi.event.VMStartEvent
import scala.tools.eclipse.debug.ActorExit

object ScalaJdiEventDispatcher {
  def apply(virtualMachine: VirtualMachine, scalaDebugTargetActor: Actor): ScalaJdiEventDispatcher = {
    val actor = new ScalaJdiEventDispatcherActor(scalaDebugTargetActor)
    actor.start
    new ScalaJdiEventDispatcher(virtualMachine, actor)
  }
}

/**
 * Actor based system pulling event from the vm event queue, and dispatching
 * them to the registered actors
 */
class ScalaJdiEventDispatcher private (virtualMachine: VirtualMachine, eventActor: ScalaJdiEventDispatcherActor) extends Runnable with HasLogger {

  @volatile
  private var running = true

  def run() {
    // the polling loop runs until the VM is disconnected, or it is told to stop.
    // The events which have been already read will still be processed by the actor.
    val eventQueue = virtualMachine.eventQueue
    while (running) {
      try {
        // use a timeout of 1s, so it cleanly terminates on shut down
        val eventSet = eventQueue.remove(1000)
        if (eventSet != null) {
          eventActor ! eventSet
        }
      } catch {
        case e: VMDisconnectedException =>
          // it is likely that we will see this exception before being able to
          // shutdown the loop after a VMDisconnectedEvent
          dispose
        case e: Throwable =>
          // it should not die from any exception. Just logging
          logger.error("Error in jdi event loop", e)
      }
    }
  }

  /**
   * release all resources
   */
  def dispose() {
    running = false
    eventActor ! ActorExit
  }

  /**
   * Register the actor as recipient of the call back for the given request
   */
  def setActorFor(actor: Actor, request: EventRequest) {
    eventActor ! ScalaJdiEventDispatcherActor.SetActorFor(actor, request)
  }

  /**
   * Remove the call back target for the given request
   */
  def unsetActorFor(request: EventRequest) {
    eventActor ! ScalaJdiEventDispatcherActor.UnsetActorFor(request)
  }

}

private[model] object ScalaJdiEventDispatcherActor {
  case class SetActorFor(actor: Actor, request: EventRequest)
  case class UnsetActorFor(request: EventRequest)
}

private[model] class ScalaJdiEventDispatcherActor(scalaDebugTargetActor: Actor) extends Actor with HasLogger {
  import ScalaJdiEventDispatcherActor._

  /**
   * event request to actor map
   */
  private var eventActorMap = Map[EventRequest, Actor]()

  def act() {
    loop {
      react {
        case SetActorFor(actor, request) =>
          setActorFor_(actor, request)
        case UnsetActorFor(request) =>
          unsetActorFor_(request)
        case eventSet: EventSet =>
          processEventSet(eventSet)
        case ActorExit =>
          exit
      }
    }
  }

  /**
   * store the actor for the request
   */
  private def setActorFor_(actor: Actor, request: EventRequest) {
    eventActorMap += (request -> actor)
  }

  /**
   * clear the actor for the request
   */
  private def unsetActorFor_(request: EventRequest) {
    eventActorMap -= request
  }

  /**
   * go through the events of the EventSet, and forward them to the registered
   * actors.
   * Resume or not the stopped threads depending on actor's answers
   */
  private def processEventSet(eventSet: EventSet) {
    var staySuspended = false

    import scala.collection.JavaConverters._

    var futures = List[Future[Boolean]]()

    def addToFutures(future: Future[Any]): Unit = {
      futures ::= future.asInstanceOf[Future[Boolean]]
    }

    /* Cannot use the eventSet directly. The JDI specification says it should implement java.util.Set,
     * but the eclipse implementation doesn't.
     * 
     * see eclipse bug #383625 */
    // forward each event to the interested actor
    eventSet.eventIterator.asScala.foreach {
      case event: VMStartEvent =>
        addToFutures(scalaDebugTargetActor !! event)
      case event: VMDisconnectEvent =>
        addToFutures(scalaDebugTargetActor !! event)
      case event: VMDeathEvent =>
        addToFutures(scalaDebugTargetActor !! event)
      case event =>
        val interestedActor = eventActorMap.get(event.request)
        if (interestedActor.isDefined) {
          addToFutures(interestedActor.get !! event)
        }
    }

    /**
     * wait for the answers of the actors and resume
     * the threads if none of the actor request to keep
     * them suspended
     */
    val it = futures.iterator
    loopWhile(it.hasNext) {
      val future = it.next
      future.inputChannel.react {
        case result: Boolean =>
          staySuspended |= result
      }
    }.andThen {
      if (!staySuspended) {
        eventSet.resume
      }
      this ! None
    }

    // invoking react here change the actor behavior. The next
    // expected message is None. All other messages will be queued until 
    // None is received
    react {
      case None =>
    }
  }
}