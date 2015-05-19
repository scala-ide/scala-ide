package debug

object CodeCompletion extends TestApp {

  def foo(mysteriousValue: Int, otherValue: Option[Float] = None) {
    val byte: Byte = 4
    val short: Short = 6
    val int = 1
    val double = 1.1
    val float = 1.1f
    val char = 'c'
    val boolean = false
    val string = "Ala"
    val mysteriousValue = List(1, 2, 3)

    val something = {
      val somethingElse = {
        val mysteriousValue = "Nothing interesting"

        Some("bar")
      }

      val mysteriousValue = 2.7

      // number of bottom line must be specified in org.scalaide.debug.internal.expression.integration.TestValues
      val debug = ???
    }

  }

  val outer = "ala"
  val someNull = null

  foo(7)
}