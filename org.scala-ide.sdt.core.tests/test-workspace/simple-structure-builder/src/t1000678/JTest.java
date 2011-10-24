package t1000678;

public class JTest {
	public static void test()  {
		Loggable.RunUnit run = new Loggable.RunUnit(4711);
		System.out.println(run.id());
	}
}
