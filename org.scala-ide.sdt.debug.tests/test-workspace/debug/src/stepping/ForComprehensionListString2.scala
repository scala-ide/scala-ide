package stepping

import debug.Helper._

object ForComprehensionListString2 {

  def main(args: Array[String]) {

    val l = List("un")

    for (n <- l) {
      n.size
    }

    noop(None)
  }
}

class ForComprehensionListString2{

}