package ticket_1001284

object Ticket1001284_3 {

  sealed trait Foo
  case class Bar(str: String, int: Int) extends Foo

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
