package t1000678_10;

public class JTest {
  public void test1() {
    Loggable.A a = new AImpl();
    /* An error is reported because classes declared 
     * inside an interface are assumed to be static, but 
     * that is not true in Scala as they have a ref to 
     * the outer member. 
     * Oddly enough, the error is not reported in the problem 
     * view, go figure... (maybe a bug in JDT?!)
     * */
    Loggable.A.B b = a.new B();
    System.out.println(b.toString());
  }

  class AImpl implements Loggable.A {

  }
}
