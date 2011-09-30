package t1000524_pos.opttest.java;

import t1000524_pos.opttest.scala.OptTest;
import scala.Option;

public class OT {
	public static <T> T getOpt1(Option<T> opt) {return OptTest.getOpt1(opt);}

	public static void main(String[] args) {
		System.out.println(getOpt1(Option.apply(1)));
	}
}
