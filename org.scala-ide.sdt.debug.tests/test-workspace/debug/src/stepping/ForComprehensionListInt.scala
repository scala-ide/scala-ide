package stepping

import debug.Helper._

object ForComprehensionListInt {

  def main(args: Array[String]): Unit = {

    val l = List(1, 2, 3, 4)

    for (n <- l) {
      noop(n)
      noop(n)
    }

    foo(l)
    new ForComprehensionListInt(l).bar
  }

  def foo(l: List[Int]): Unit = {

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

  def bar(): Unit = {

    for (n <- l) {
      noop(n)
      noop(n)
    }

  }

}