package scala.tools.eclipse.debug

import scala.actors.Actor
import scala.actors.Exit
import scala.actors.UncaughtException
import scala.collection.mutable.Stack
import scala.tools.eclipse.logging.HasLogger
import com.sun.jdi.VMDisconnectedException
import scala.tools.eclipse.ScalaPlugin

/** A generic message to inform that an actor should terminate. */
object PoisonPill

/** Base class used by all actors in the debugger module.
  *
  * This base class for actors provides a number of interesting functionalities:
  *
  * - Automatic logging of all messages processed by the actor. This can be quite useful when debugging.
  * - Stackable behaviors. By calling `become/unbecome` an actor can easily modify the shape of
  * messages that it can process.
  * - Cleaner actor's life-cylce. Basically, when starting an actor, `postStart` hook is guaranteed
  * to be called before the processing of any message. Then, the actor will process messages accordingly
  * to its `behavior` implementation. If an `Exit` or `PoisonPill` message is processed by `this` actor,
  * the actor is guaranteed to execute `preExit` before terminating.
  */
trait BaseDebuggerActor extends Actor with HasLogger {
  // Always send an `Exit` message when a linked actor terminates. Be aware that if you change this,
  // you'll have to revisit the whole system behavior, as the system relies on `Exit` messages for
  // graceful termination of all linked actors.
  trapExit = true

  type Behavior = PartialFunction[Any, Unit]

  private var poisoned: Boolean = false

  /** Stacks of behaviors for this actor. If empty, `currentBehavior` is used. */
  private val behaviors: Stack[Behavior] = {
    val initialBehavior = behavior orElse exitBehavior
    Stack(initialBehavior)
  }

  /** This method allows to log all messages processed by `this`.
    * One important requirement is that logging doesn't affect the actor's behavior (note how
    * `isDefinedAt` has been implemented, it uses the `currentBehavior` to know if the message
    * can be processed by `this` actor.
    */
  private def loggingBehavior: PartialFunction[Any, Any] = new PartialFunction[Any, Any] {
    override def isDefinedAt(msg: Any): Boolean = currentBehavior.isDefinedAt(msg)
    override def apply(msg: Any): Any = {
      // this should be moved to logger.trace, once it is available
      logger.debug("Processing message " + msg + " from " + sender)
      msg
    }
  }

  override final def act(): Unit = {
    postStart()
    //FIXME: Re-enable this the moment we have TRACE log level available (ticket #1001307)
    loop { react { /*loggingBehavior andThen*/ currentBehavior } }
  }

  private def currentBehavior: Behavior = behaviors.top

  /** This method is called exactly once right after the actor is started, but before the first message is removed from the actor's mailbox.
    * For instance, you should override `postStart` if you need to link actors together.
    */
  protected def postStart(): Unit = ()

  /** Defines the behavior of `this` actor.
    * Warning: Never, ever, use wildcard messages, or the `exitBehavior` won't be executed (and this could prevent correct termination
    * of the whole system).
    */
  protected def behavior: Behavior

  /** Defines the exit behavior of `this` actor.
    * The current default is to terminate the actor execution if a linked actor terminates (independently of the termination's reason).
    * Before stopping the actor, `beforeExit` is called on `this` actor to execute any clean-up logic.
    */
  private def exitBehavior: Behavior = {
    case PoisonPill =>
      // don't delete this, I'll re-enable the log as soon as we have TRACE log level.
      // logger.debug("Actor " + this + " is shutting down...")
      shutdown()
    // Termination message sent by any linked actor. `reason` can be 'normal or an error, i.e., an exception.
    case msg @ Exit(from, reason) =>
      // don't delete this, I'll re-enable the log as soon as we have TRACE log level.
      //logger.debug("Received %s, shutting down...".format(msg))
      shutdown()
  }

  private def shutdown(): Nothing = {
    preExit()
    exit()
  }

  /** Change the set of messages that `this` actor can process. You can restore the former actor's behavior by simply calling `unbecome` unless
    * this actor was asked to terminate, in which case the `become` request is ignored.
    */
  protected final def become(newBehavior: Behavior): Unit = {
    if (poisoned) logger.info("Ignoring `become` call because " + this + " was asked to terminate and not process any further message.")
    else behaviors push newBehavior
  }

  /** Call this method when you want to restore the former actor's behavior. If this actor was asked to terminate, or if no call to `become` were
    * performed prior to calling `unbecome`, then the `unbecome` request is ignored.
    */
  protected final def unbecome(): Unit = {
    if (poisoned) logger.info("Ignoring `unbecome` call because " + this + " was asked to terminate and not process any further message.")
    else if (behaviors.size > 1) behaviors.pop
    else logger.info("Ignoring call to `unbecome` in actor " + this)
  }

  /** Before stopping the actor, `preExit` is called on `this` actor to execute any clean-up logic. Mind that `preExit` is not
    * called if an unexpected exception occurs while the actor is executing (this is done to prevent infinite recursion), or if `exit`
    * is explicitly called.
    */
  protected def preExit(): Unit = ()

  /** Terminate `this` actor after finishing processing the current message. */
  final protected def poison(): Unit = {
    if (!poisoned) {
      behaviors.clear()
      behaviors push exitBehavior
      poisoned = true
      this ! PoisonPill
    }
  }

  override def exceptionHandler: Behavior = {
    // Given we have a highly asynchronous system, `VMDisconnectedException` can happen simply because the user has stopped a debug session
    // while some debug actor was executing some logic that requires the underline virtual machine to be up and running. These sort of
    // exception do not provide any meaningful information, hence we simply swallow it.
    case vme: VMDisconnectedException => ()
    case e: Exception =>
      eclipseLog.error("Shutting down " + this + " because of", e)
      val reason = UncaughtException(this, Some("Unhandled exception while actor %s was still running.".format(this)), Some(sender), Thread.currentThread(), e)
      exit(reason)
  }
}

object BaseDebuggerActor {
  val TIMEOUT = 5000 // ms

  /** A timed send with a default timeout. */
  val syncSend = timedSend(TIMEOUT) _

  /** Synchronoous message send with timeout. If the plugin is configured to run without timeouts,
   *  it blocks until a reply is received.
   *
   *  @see ScalaPlugin.noTimeoutMode
   */
  def timedSend(timeout: Int)(a: Actor, msg: Any): Option[Any] = {
    if (ScalaPlugin.plugin.noTimeoutMode)
      Some(a !? msg)
    else
      a !? (timeout, msg)
  }
}
