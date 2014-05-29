package debug

object Arrays extends App {

  def arrayIdentity[A](array: Array[A]) = array

  def foo() {
    val intArray = Array(1, 2, 3)
    val stringArray = Array("Ala", "Ola", "Ula")

    // number of bottom line must be specified in org.scalaide.debug.internal.expression.integration.TestValues object because a lot of tests use this line
    val debug = ???
  }

  foo()
}