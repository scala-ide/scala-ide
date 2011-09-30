package t1000524_2.opttest.java;

import java.util.List;

import t1000524_2.opttest.scala.OptTest;
import scala.Option;


public class OT {
	public static <T> T getOpt1(Option<T> opt) {return OptTest.getOpt1(opt);}
	
	public static <T extends Comparable<T>> T getOpt2(Option<T> opt) {return OptTest.getOpt2(opt);}
	
	public static <T extends Comparable<T>, S extends Comparable<S>> T getOpt2(Option<T> opt, List<S> elements) {
		return OptTest.getOpt3(opt, elements);
	}
	
	public static void main(String[] args) {
		System.out.println(getOpt1(Option.apply(1)));
	}
}
