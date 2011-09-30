package t1000524_1.opttest.java;

import t1000524_1.opttest.scala.OptTest;
import scala.Option;

public class OT {
	public static <T> T getOpt2(Option<T> opt) {return OptTest.getOpt2(opt);}

	public static void main(String[] args) {
		System.out.println(getOpt2(Option.apply(2)));
	}
}
