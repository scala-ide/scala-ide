package debug

object Nested extends TestApp {

  def outer(): Int = {

    val outerUsed = 1
    val outerUnused = 2

    def inner(): Int = {
      val result = outerUsed + 1

      // number of following line must be specified in
      // org.scalaide.debug.internal.expression.integration.TestValues object because a lot of tests use it
      val breakpointHere = ???
      result
    }

    inner()
  }

  outer()
}