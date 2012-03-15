package stepping

import debug.Helper._

object AnonFunOnListString {

  def main(args: Array[String]) {

    val l = List("un", "deux", "quatre", "huit")

    l.foreach(noop(_))

    l.find(_.isEmpty)
      
    l.map(_.size)

    l.foldLeft(0)(_ + _.size)

  }

}

class AnonFunOnListString {}