package stepping

import debug.Helper._

object AnonFunOnListInt {

  def main(args: Array[String]) {

    val l = List(1, 2, 4, 8)

    l.foreach(noop(_))

    l.find(_ == 3)

    l.map(ret(_))

    l.foldLeft(0)(_ + ret(_))

    l foreach { i =>
      noop(i)
    }

  }

}

class AnonFunOnListInt {}