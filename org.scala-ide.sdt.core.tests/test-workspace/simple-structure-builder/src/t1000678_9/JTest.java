package t1000678_9;

public class JTest {
  public void test1() {
    AImpl a = new AImpl();
    Loggable.A.B b = a.new BImpl();
    System.out.println(b.toString());
  }

  class AImpl implements Loggable.A {
    class BImpl extends B {

    }
  }
}
