package breakpoints

class A_1001367

object A_1001367 {
  class B {
    val a = 2 /* put breakpoint at this line */
  }

  def main(args: Array[String]) {
    new B().a
    println("test")
  }
}