package stepping

import debug.Helper._

class SimpleStepping {

  def foo(): Unit = {
    bar()  // line 8
  }

  def bar(): Unit = {
    noop(None) // line 12
    noop(None) // line 13
  }

  def mainTest(): Unit = {
    foo()
  }
}

object SimpleStepping {

  def main(args: Array[String]): Unit = {
    new SimpleStepping().mainTest
  }

}

