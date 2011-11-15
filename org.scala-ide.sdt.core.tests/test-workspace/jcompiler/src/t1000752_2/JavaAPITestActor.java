package t1000752_2;

public class JavaAPITestActor extends UntypedActor {
    public void onReceive(Object msg) {
        getSender().tell("got it!");
    }
}