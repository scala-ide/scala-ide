package debug

object Java extends TestApp {

  def foo(): Unit = {
    val javaLibClass = new JavaLibClass()
    // following line contains breakpoint, defined in TestValues.JavaTestCase
    val bp = ???
  }

  foo()
}
