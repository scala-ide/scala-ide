package method_with_type_contravariance;

import java.util.ArrayList;

public class Foo {
	public <T extends Foo> T foo(ArrayList<? super Class<T>> xs, T t) {
		Scala s = new Scala();
		return s.foo(xs,t);
	}
}
