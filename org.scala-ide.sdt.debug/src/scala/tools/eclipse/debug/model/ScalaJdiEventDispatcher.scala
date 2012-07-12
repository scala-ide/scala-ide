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

case class SetActorFor(actor: Actor, request: EventRequest)
case class UnsetActorFor(request: EventRequest)

/**
 * Actor based system pulling event from the vm event queue, and dispatching
 * them to the registered actors
 */
class ScalaJdiEventDispatcher(virtualMachine: VirtualMachine, scalaDebugTargetActor: Actor) extends Runnable with HasLogger {

  var running = true

  def run() {
    // the polling loop
    val eventQueue = virtualMachine.eventQueue
    while (running) {
      try {
        // use a timeout of 1s, so it cleaning terminate on shut down
        val eventSet = eventQueue.remove(1000)
        if (eventSet != null) {
          eventActor ! eventSet
        }
      } catch {
        case e: VMDisconnectedException =>
          logger.error("Error in jdi event loop", e)
          running = false
        case e =>
          logger.error("Error in jdi event loop", e)
      }
    }
  }

  val eventActor= new Actor {
    start

    /**
     * event request to actor map
     */
    var eventActorMap = Map[EventRequest, Actor]()

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
    def setActorFor_(actor: Actor, request: EventRequest) {
      eventActorMap += (request -> actor)
    }

    /**
     * clear the actor for the request
     */
    def unsetActorFor_(request: EventRequest) {
      eventActorMap -= request
    }

    /**
     * go through the events of the EventSet, and forward them to the registered
     * actors.
     * Resume or not the stopped thread depending on actor's answers
     */
    def processEventSet(eventSet: EventSet) {
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
      eventSet.eventIterator.asScala.foreach {
        case event: VMStartEvent =>
          logger.debug("Processing event: %s".format(event))
          addToFutures(scalaDebugTargetActor !! event)
        case event: VMDisconnectEvent =>
          logger.debug("Processing event: %s".format(event))
          running = false
          addToFutures(scalaDebugTargetActor !! event)
        case event: VMDeathEvent =>
          logger.debug("Processing event: %s".format(event))
          running = false
          addToFutures(scalaDebugTargetActor !! event)
        case event =>
          val interestedActor = eventActorMap.get(event.request)
          logger.debug("Processing event: %s for %s".format(event, interestedActor))
          if (interestedActor.isDefined) {
            addToFutures(interestedActor.get !! event)
          }
      }

      /**
       * wait for the answers of the actors and resume
       * the threads if needed
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

      react {
        case None =>
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
    eventActor ! SetActorFor(actor, request)
  }

  /**
   * Remove the call back target for the given request
   */
  def unsetActorFor(request: EventRequest) {
    eventActor ! UnsetActorFor(request)
  }

}

