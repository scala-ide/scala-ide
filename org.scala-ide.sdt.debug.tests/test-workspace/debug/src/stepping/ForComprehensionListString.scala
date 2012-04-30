package stepping

object ForComprehensionListString {

  def main(args: Array[String]) {

    val l = List("un", "deux", "quatre", "huit")

    for (n <- l) {
      n.size
    }

    foo(l)
    new ForComprehensionListString(l).bar
  }

  def foo(l: List[String]) {

    for (n <- l) {
      n.size
    }

  }

}

class ForComprehensionListString(l: List[String]) {

  for (n <- l) {
    n.size
  }

  def bar() {

    for (n <- l) {
      n.size
    }

  }

}