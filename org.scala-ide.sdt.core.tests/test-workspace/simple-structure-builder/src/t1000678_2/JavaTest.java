package t1000678_2;

public class JavaTest {
	public void test() {
		A a = new A(); 
		A.B b = a.new B();
		b.foo();
		
		A.C c = a.new C();
		c.foo();
		A.C.D d = c.new D();
		d.foo();
	}
}
