package debug

class Throwing {
  def foo(arg: Int): Int = throw new IllegalArgumentException("Your argument is invalid")
}

object Exceptions extends TestApp {

  def testMethod(): Unit = {
    val throwing = new Throwing()

    val breakpoint = ???
  }

  testMethod()
}