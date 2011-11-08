package t1000678_8;

public class JTest {
  public static void test() {
    Loggable.RunUnit run = new Loggable.RunUnit(4711);
    System.out.println(run.id());

    Loggable.RunUnit.IC c = run.new C();
    c.foo();
  }
  
  
  public static void test2() {
    Loggable.IRunUnit run = new Loggable.RunUnit(4711);
    System.out.println(run.toString());
  }
}
