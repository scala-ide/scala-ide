package stepping

object ForComprehensionListString {

  def main(args: Array[String]): Unit = {

    val l = List("un", "deux", "quatre", "huit")

    for (n <- l) {
      n.size
    }

    foo(l)
    new ForComprehensionListString(l).bar
  }

  def foo(l: List[String]): Unit = {

    for (n <- l) {
      n.size
    }

  }

}

class ForComprehensionListString(l: List[String]) {

  for (n <- l) {
    n.size
  }

  def bar(): Unit = {

    for (n <- l) {
      n.size
    }

  }

}