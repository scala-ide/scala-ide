package debug
object NestedLambda extends App {
  List(1, 3, 5).map { elem =>
    val inner = new A
    val mapper = inner.foo _
    mapper(elem > 0)
  }
}
class A {
  def foo(silent: Boolean) = {
    (new Inner1).inner.foo()
  }
  private class Inner1 {
    class Inner {
      def foo(silent: Boolean = false): String = {
        val result = "trala".nonEmpty.toString()
        result
      }
    }
    def inner = new Inner
  }
}
