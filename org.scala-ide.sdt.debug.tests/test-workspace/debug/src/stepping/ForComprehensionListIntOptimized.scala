package stepping

import debug.Helper._

object ForComprehensionListIntOptimized {

  def main(args: Array[String]): Unit = {

    val l = List(1, 2, 3, 4)

    for (n <- l) {
      noop(n)
    }

    foo(l)
    new ForComprehensionListIntOptimized(l).bar
  }

  def foo(l: List[Int]): Unit = {

    for (n <- l) {
      noop(n)
    }

  }

}

class ForComprehensionListIntOptimized(l: List[Int]) {

  for (n <- l) {
    noop(n)
  }

  def bar(): Unit = {

    for (n <- l) {
      noop(n)
    }

  }

}