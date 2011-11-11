package t1000752_2

abstract class ActorRef extends Serializable {
  scalaRef: ScalaActorRef ⇒

  def name: String
  
  def address: String
  
  def compareTo(other: ActorRef) = this.address compareTo other.address
  
  def tell(msg: Any): Unit = this.!(msg)
  
  def tell(msg: Any, sender: ActorRef): Unit
  
  def ask(message: AnyRef, timeout: Long): Future[AnyRef] = ?(message, timeout).asInstanceOf[Future[AnyRef]]
  
  def forward(message: Any) = tell(message, null)
  
  def suspend(): Unit
  
  def resume(): Unit
  
  def stop(): Unit
  
  def isShutdown: Boolean
  
  def startsMonitoring(subject: ActorRef): ActorRef //TODO FIXME REMOVE THIS
  
  def stopsMonitoring(subject: ActorRef): ActorRef //TODO FIXME REMOVE THIS
  
  override def equals(that: Any): Boolean = {
    that.isInstanceOf[ActorRef] &&
      that.asInstanceOf[ActorRef].address == address
  }
  
  override def toString = "Actor[%s]".format(address)
}

trait ScalaActorRef { ref: ActorRef ⇒
  def !(message: Any)(implicit sender: ActorRef = null): Unit = ref.tell(message, sender)
  def ?(message: Any)(implicit timeout: Any): Future[Any]
  def ?(message: Any, timeout: Any)(implicit ignore: Int = 0): Future[Any] = ?(message)(timeout)
}

