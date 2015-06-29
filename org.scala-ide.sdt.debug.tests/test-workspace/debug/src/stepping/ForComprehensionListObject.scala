package stepping

object ForComprehensionListObject {

  def main(args: Array[String]): Unit = {

    val l= List(new Object(), "deux", "quatre", "huit")

    for (n <- l) {
      n
    }

    foo(l)
    new ForComprehensionListObject(l).bar
  }

  def foo(l: List[Object]): Unit = {

    for (n <- l) {
      n
    }

  }

}

class ForComprehensionListObject (l: List[Object]) {

  for (n <- l) {
    n
  }

  def bar(): Unit = {

    for (n <- l) {
      n
    }

  }

}