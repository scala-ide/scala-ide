package t1000752_2

object Actor {
  type Receive = PartialFunction[Any, Unit]
}

trait Actor {
  import Actor._

  type Receive = Actor.Receive

  trait MessageDispatcher

  def loggable(self: AnyRef)(r: Receive): Receive = null

  def someSelf: Some[ActorRef with ScalaActorRef] = Some(null)

  def optionSelf: Option[ActorRef with ScalaActorRef] = someSelf

  implicit def self = someSelf.get

  @inline
  final def sender: ActorRef = null

  def receiveTimeout: Option[Long] = None

  def receiveTimeout_=(timeout: Option[Long]) {}

  def children: Iterable[ActorRef] = null

  def dispatcher: MessageDispatcher = null

  protected def receive: Receive

  def preStart(): Unit = {}

  def postStop(): Unit = {}

  def preRestart(reason: Throwable, message: Option[Any]): Unit = { postStop() }

  def postRestart(reason: Throwable): Unit = { preStart() }

  def unhandled(message: Any): Unit = {}

  def become(behavior: Receive, discardOld: Boolean = true): Unit = { }

  def unbecome(): Unit = { }

  def watch(subject: ActorRef): ActorRef = self startsMonitoring subject

  def unwatch(subject: ActorRef): ActorRef = self stopsMonitoring subject

  private[this] final def apply(msg: Any): Unit = {}

  private val processingBehavior = receive
}

