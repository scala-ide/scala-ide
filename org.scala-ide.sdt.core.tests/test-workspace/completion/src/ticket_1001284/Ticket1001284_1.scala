package ticket_1001284

object Ticket1001284_1 {

  case class Bar(str: String, int: Int)

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
