package t1000678_3;

public class JavaTest {
	public static void test()  {
		Loggable.RunUnit run = new Loggable.RunUnit(4711);
		System.out.println(run.id());
		
		Loggable.RunUnit.C c = run.new C();
		c.foo();
	}
}
