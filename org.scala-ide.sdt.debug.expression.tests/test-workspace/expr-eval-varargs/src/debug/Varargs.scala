package debug

object Varargs extends App {

  val x = 13
  val y = 17

  val i1 = 1
  val i2 = 2
  val i3 = 3
  val i4 = 4

  val l1 = 1L
  val l2 = 2L
  val l4 = 4L

  def fun() = x

  def fun(a: Int) = a + x

  def fun(a: Long) = a + y

  // is never used due to fun(a: Int), fun(a: Int, b: Int) and def fun(a: Int, b: Int, c: Int*)
  def fun(c: Int*) = c.sum + x * 2

  def fun(c: Long*) = c.sum + y * 2

  def fun(a: Int, b: Int) = a + b + x * 3

  def fun(a: Long, b: Long) = a + b + y * 3

  def fun(a: Int, b: Int, c: Int*) = a + b + c.sum + x * 4

  def fun(a: Long, b: Int, c: Int*) = a + b + c.sum + y * 4

  def fun2(c: Int*) = c.sum + x * 5

  def fun2(a: Long, b: Int*) = a + b.sum + y * 5

  def foo() {

    // number of bottom line must be specified in org.scalaide.debug.internal.expression.integration.TestValues object because a lot of tests use this line
    val debug = ???
  }

  foo()
}
