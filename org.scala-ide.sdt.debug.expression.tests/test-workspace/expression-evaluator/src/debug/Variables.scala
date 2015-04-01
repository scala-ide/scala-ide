package debug

class State {
  var int: Int = 1
}

object Variables extends TestApp {

  var fieldInt: Int = 1
  var fieldString = "Ala"
  var anotherStringField = "Ola"

  def foo() {
    var localInt: Int = 2
    var localBoxedInt: java.lang.Integer = 2
    var localString: String = "qwe"
    val state = new State()
    // number of bottom line must be specified in org.scalaide.debug.internal.expression.integration.TestValues object because a lot of tests use this line
    val debug = 1
  }

  foo()
}
