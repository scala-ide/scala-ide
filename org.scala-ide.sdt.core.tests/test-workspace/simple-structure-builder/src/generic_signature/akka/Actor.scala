package generic_signature.akka

class ActorRef

class Actor2 {
  def actorOf(clazz: Class[_ <: Actor]): ActorRef = new ActorRef
}


object Actor3 {
  def actorOf(clazz: Class[_ <: Actor]): ActorRef = new ActorRef
}