package debug

object Java extends App {

  def foo(): Unit = {
    val javaLibClass = new JavaLibClass()
    // following line contains breakpoint, defined in TestValues.JavaTestCase
    val bp = ???
  }

  foo()
}
