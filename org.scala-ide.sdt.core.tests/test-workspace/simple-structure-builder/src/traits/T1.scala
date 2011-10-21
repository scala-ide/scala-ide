package traits

/** A simple test for the structure builder */
trait T1 {
  def notImplemented = error("not implemented")
  
  val x: Int
  val y: Long
  val t = new Object
  val xs = List(1, 2, 3)
  
  def m(x: Int, y: Int) = notImplemented
  def n(x: Int, y: Int)(z: Int) = notImplemented
  
  type T = List[Int]
  type U <: Set[Long]
  type Z
  
  class Inner

  class InnerWithGenericParams(xs: List[Int]) {
  }
}
