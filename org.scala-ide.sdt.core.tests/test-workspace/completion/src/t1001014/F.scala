package t1001014

object F {
  case class A(val xx: Int)

  val a = A(42)
  a.x /*!*/
}