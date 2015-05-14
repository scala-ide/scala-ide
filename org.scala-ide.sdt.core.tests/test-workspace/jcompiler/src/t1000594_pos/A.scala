package t1000594_pos

trait A {

  def foo(n: Int): Unit = {
    Some(n)
  }


  def foo(s: List[Option[String]]): Unit = {}

  private def foo(n: Set[String]): Unit = {}

  def foo(n: Option[String]): Unit = {}

  def foo(s: String, n: Option[String]): Int => Unit = foo(s)(n) _

  def foo(s: String)(n: Option[String])(n2: Int): Unit = {}

}

abstract class B extends A