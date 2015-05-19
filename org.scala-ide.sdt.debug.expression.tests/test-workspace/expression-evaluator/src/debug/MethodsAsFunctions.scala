package debug

trait TestMethods {
  def zero() = 0

  def inc(i: Int) = i + 1

  def _dec(i: Int) = i - 1
  val dec = _dec _

  def sum(i1: Int, i2: Int) = i1 + i2

  val nat = Array(2, 3, 4)
}

object MethodsAsFunctions extends TestApp {

  trait InnerTrait extends TestMethods {
    def breakpoint = "here"
  }

  class InnerClass extends TestMethods {
    def breakpoint = "here"
  }

  object InnerObject extends TestMethods {
    def breakpoint = "here"
  }

  (new InnerTrait {}).breakpoint
  (new InnerClass).breakpoint
  (InnerObject).breakpoint
}

object ObjectMethods extends TestMethods