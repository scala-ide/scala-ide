package generic_signature.akka;

public class Actor {
	public static ActorRef actorOf2(Class<? extends Actor> clazz) {
		return new Actor2().actorOf(clazz); 
	}
	public static ActorRef actorOf3(Class<? extends Actor> clazz) {
		return Actor3.actorOf(clazz); 
	}
}
