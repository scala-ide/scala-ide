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

  override def preStart() {}

  override def postStop() {}

  override def preRestart(reason: Throwable, lastMessage: Option[Any]) {}

  override def postRestart(reason: Throwable) {}

  override def unhandled(msg: Any) {
    throw new Exception
  }

  final protected def receive = {
    case msg ⇒ onReceive(msg)
  }
}