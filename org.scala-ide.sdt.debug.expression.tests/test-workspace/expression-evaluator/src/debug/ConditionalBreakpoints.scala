package debug

object ConditionalBreakpoints extends TestApp {

  def foo() {

    val int = 1

    val debug = ??? //must be in line 9 cos most of test use this line

  }

  foo()

}