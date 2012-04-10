package stepping

import debug.Helper._

object ForComprehensionListIntOptimized {

  def main(args: Array[String]) {

    val l = List(1, 2, 3, 4)

    for (n <- l) {
      noop(n)
    }

    foo(l)
    new ForComprehensionListIntOptimized(l).bar
  }

  def foo(l: List[Int]) {

    for (n <- l) {
      noop(n)
    }

  }

}

class ForComprehensionListIntOptimized(l: List[Int]) {

  for (n <- l) {
    noop(n)
  }

  def bar() {

    for (n <- l) {
      noop(n)
    }

  }

}