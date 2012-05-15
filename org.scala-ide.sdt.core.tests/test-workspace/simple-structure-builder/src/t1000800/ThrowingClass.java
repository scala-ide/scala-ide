package t1000800;

public class ThrowingClass implements ThrowingInterface {
	public void throwingMethod() throws InterruptedException {
		Thread.sleep(1000);
	}
}
