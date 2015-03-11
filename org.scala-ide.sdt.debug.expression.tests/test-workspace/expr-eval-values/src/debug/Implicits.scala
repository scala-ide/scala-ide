package debug

object Implicits extends App {

  import ImplicitsValues2._
  import ImplicitsValues.implDouble

  def foo() {
    val libClass = LibClass(1)

    implicit val implInt = 12



    val debug = ???
  }

  foo()
}

object ImplicitsValues {
  implicit val implDouble = 1.1
}

object ImplicitsValues2 {
  implicit val implString = "ala"
  implicit val implDouble2 = 2.2
}

object ImplicitsValues3 {
  implicit final class ArrowAssocWithoutAnyVal[A](private val self: A) {
    def --> [B](y: B): Tuple2[A, B] = Tuple2(self, y)
  }
}