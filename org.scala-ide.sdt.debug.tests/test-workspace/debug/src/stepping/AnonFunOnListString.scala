package stepping

import debug.Helper._

object AnonFunOnListString {

  def main(args: Array[String]): Unit = {

    val l = List("un", "deux", "quatre", "huit")

    a(l)
    b(l)
    c(l)
    d(l)
  }

  def a(l: List[String]): Unit = {

    l.foreach(noop(_))

  }

  def b(l: List[String]): Unit = {

    l.find(_.isEmpty)

  }

  def c(l: List[String]): Unit = {

    l.map(_.size)

  }

  def d(l: List[String]): Unit = {

    l.foldLeft(0)(_ + _.size)

  }

}

class AnonFunOnListString {}