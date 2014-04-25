package debug

class State {
  var int: Int = 1
}

object Variables extends App {

  var fieldInt: Int = 1

  def foo() {
    var localInt: Int = 2
    val state = new State()
    // number of bottom line must be specified in org.scalaide.debug.internal.expression.integration.TestValues object because a lot of tests use this line
    val debug = 1
  }

  foo()
}