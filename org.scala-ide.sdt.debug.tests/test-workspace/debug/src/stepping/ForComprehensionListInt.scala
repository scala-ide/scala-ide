package stepping

import debug.Helper._

object ForComprehensionListInt {

  def main(args: Array[String]) {

    val l = List(1, 2, 3, 4)

    for (n <- l) {
      noop(n)
      noop(n)
    }

    foo(l)
    new ForComprehensionListInt(l).bar
  }

  def foo(l: List[Int]) {

    for (n <- l) {
      noop(n)
      noop(n)
    }

  }

}

class ForComprehensionListInt(l: List[Int]) {

  for (n <- l) {
    noop(n)
    noop(n)
  }

  def bar() {

    for (n <- l) {
      noop(n)
      noop(n)
    }

  }

}