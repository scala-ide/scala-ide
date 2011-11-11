package t1000752_3

trait Top {
  protected def foo(t: Array[String])
}

abstract class ATop extends Top {
  override def foo(t: Array[String]) {}
}