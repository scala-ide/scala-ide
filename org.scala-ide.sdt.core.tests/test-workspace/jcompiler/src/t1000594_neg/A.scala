package t1000594_neg

trait A {
  def foo(s: List[Option[String]]): Unit
}

abstract class B extends A