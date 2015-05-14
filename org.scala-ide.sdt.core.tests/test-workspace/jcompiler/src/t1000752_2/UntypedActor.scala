package t1000752_2

abstract class UntypedActor extends Actor {
  trait Procedure[+T]

  @throws(classOf[Exception])
  def onReceive(message: Any): Unit

  def getSelf(): ActorRef = self

  def getSender(): ActorRef = sender

  def getReceiveTimeout: Option[Long] = receiveTimeout

  def setReceiveTimeout(timeout: Long): Unit = receiveTimeout = Some(timeout)

  def getChildren(): java.lang.Iterable[ActorRef] = {
    null
  }

  def getDispatcher(): MessageDispatcher = dispatcher

  def become(behavior: Procedure[Any]): Unit = become(behavior, false)

  def become(behavior: Procedure[Any], discardOld: Boolean): Unit =
    super.become(null, discardOld)

  override def preStart(): Unit = {}

  override def postStop(): Unit = {}

  override def preRestart(reason: Throwable, lastMessage: Option[Any]): Unit = {}

  override def postRestart(reason: Throwable): Unit = {}

  override def unhandled(msg: Any): Unit = {
    throw new Exception
  }

  final protected def receive = {
    case msg ⇒ onReceive(msg)
  }
}