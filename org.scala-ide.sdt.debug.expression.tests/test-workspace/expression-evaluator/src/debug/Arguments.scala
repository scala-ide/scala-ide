package debug

object Arguments extends TestApp {

  val int = 1
  val double = 1.1
  val list = List(1, 2, 3)

  def testFunction(int: Int, double: Double, list: List[Int]) {
    // number of bottom line must be specified in org.scalaide.debug.internal.expression.integration.TestValues object because a lot of tests use this line
    val debug = "ala"
  }

  testFunction(123, 230.0, List(5, 10, 15))
}