package debug

object Arrays extends TestApp {

  def arrayIdentity[A](array: Array[A]) = array

  def foo() {
    val emptyArray = Array[Int]()

    val intArray = Array(1, 2, 3)

    val stringArray = Array("Ala", "Ola", "Ula")

    val nestedArray = Array(
      Array(1, 2, 3),
      Array(4, 5, 6),
      Array(7, 8, 9))

    val nestedObjectArray = Array(
      Array("1", "2", "3"),
      Array("4", "5", "6"),
      Array("7", "8", "9"))

    // number of bottom line must be specified in org.scalaide.debug.internal.expression.integration.TestValues object because a lot of tests use this line
    val debug = ???
  }

  foo()
}