package ticket_1001284

object Ticket1001284_2 {

  class Bar(str: String, int: Int)
  object Bar {
    def unapply(bar: Bar): Option[(String, Int)] = ???
    def apply(str: String, int: Int): Bar = ???
  }

  val bar = Bar("FooBar", 42)

  // 1.
  val Bar( /*!*/ )

  // 2.
  val Bar( /*!*/ ) = bar

  // 3.
  bar match {
    case Bar( /*!*/ )
  }

  // 4.
  bar match {
    case Bar( /*!*/ ) =>
  }
}
