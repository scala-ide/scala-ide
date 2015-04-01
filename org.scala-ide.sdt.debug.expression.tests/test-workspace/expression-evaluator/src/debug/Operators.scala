package debug

case class %%%(i: Int)
case class ^^^(ss: String*)

object OperatorsObj {
  val ++ = 1
  var +:+ = 2
  def !!! = 1
  def @@@() = 2
  def ###(i: Int) = i
  def $$$(ss: String*) = ss
}

class Operators {
  val ++ = 1
  var +:+ = 2
  def !!! = 1
  def @@@() = 2
  def ###(i: Int) = i
  def $$$(ss: String*) = ss
}

object Operators extends TestApp {
  val ++ = 1
  var +:+ = 2

  def !!! = 1
  def @@@() = 2
  def ###(i: Int) = i
  def $$$(ss: String*) = ss

  val operators = new Operators

  val list = List(1, 2, 3)

  def foo() {
    val -- = 123
    var -:- = "456"
    // breakpoint here
    val bp = 1
  }

  foo()
}