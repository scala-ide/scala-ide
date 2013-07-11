package test

class Foo {
  def competitors() {
    List("foo", "bar") match {
      case Nil => Nil
      case competitors =>
        def bindCompetitors(): List[Nothing] =
          competitors.flatMap { competitor =>
            val l: Int = competitor.length
            l.foo.toString
          }
        Nil
    }
  }
}