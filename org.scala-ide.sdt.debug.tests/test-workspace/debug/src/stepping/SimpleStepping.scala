package stepping

import debug.Helper._

class SimpleStepping {

  def foo() {
    bar()  // line 8
  }

  def bar() {
    noop(None) // line 12
    noop(None) // line 13
  }

  def mainTest() {
    foo()
  }
}

object SimpleStepping {

  def main(args: Array[String]) {
    new SimpleStepping().mainTest
  }

}

