package stepping

import debug.Helper._

object AnonFunOnListString {

  def main(args: Array[String]) {

    val l = List("un", "deux", "quatre", "huit")

    a(l)
    b(l)
    c(l)
    d(l)
  }

  def a(l: List[String]) {

    l.foreach(noop(_))

  }

  def b(l: List[String]) {

    l.find(_.isEmpty)

  }

  def c(l: List[String]) {

    l.map(_.size)

  }

  def d(l: List[String]) {

    l.foldLeft(0)(_ + _.size)

  }

}

class AnonFunOnListString {}