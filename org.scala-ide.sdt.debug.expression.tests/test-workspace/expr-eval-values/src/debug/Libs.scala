package debug

object Libs {

  def libMultipleParamers(a: Int)(b: Int) = a + b

  def function(a: Int) = a + 1

  val nullVal = null
  val nullValString: String = null
  val nullValArray: Array[String] = null

  def nullDef = null
  def nullDefString: String = null
  def nullDefArray: Array[String] = null

  def nullCheck(a: Any) = a == null

  val value = 11

  val intArray = Array(1, 2, 3)

  val stringArray = Array("Ala", "Ola", "Ula")

  val existentialList: List[_] = List(1, "2")

  val existentialMultilist: List[List[_]] = List(List(1, "2"))

  override def toString: String = "Libs - object"
}

class LibClassWithoutArgs {
  override def toString: String = "LibClassWithoutArgs"
}

case class LibClass(a: Int) {

  private var i = a

  def selfRef(): LibClass = this

  def perform[A](func: Int => A) = func(a)

  def performByName(func: => Int) = func + a

  def performByNameGen[A](func: => A) = func

  def performTwice(func: => Int) = func + func

  def incrementAndGet() = {
    i += 1
    i
  }

  def performOnList[A](func: List[Int] => A): A = func(List(1, 2))
}

case class LibClass2Lists(a: Int)(b: Int)

case class LibClassWithVararg(a: Int*)

object LibObject {
  val id = 1
  val libObj1 = "libObj1"

  class LibNestedClass {
    object LibMoreNestedObject {
      val id = 4
    }
  }

  val nestedClass = new LibNestedClass()

  object LibNestedObject {
    val id = 2

    val libObj2 = "libObj2"


    object LibMoreNestedObject {
      val id = 3
    }
  }
}

sealed class NatNum(val n : Int)
object Nat1 extends NatNum(1)
object Nat2 extends NatNum(2)
object Nat3 extends NatNum(3)

object LibVarargs {

  case class Lst(l: List[NatNum])

  implicit val implicitList = Lst(List(Nat1, Nat2))

  trait LibTraitVarargs {
    def sub(a: NatNum)(b: Int) = a.n - b
    def sum(s: NatNum)(m: Int*) = m.map(_ + s.n).toList
    def mul(m: Int*)(s: NatNum) = m.map(_ * s.n).toList
    def product(l1: Int*)(implicit l2: Lst) = (for { i1 <- l1; i2 <- l2.l } yield i1 * i2.n).toList

    def sum3(a: Int)(b: NatNum)(c: Int*) = a + b.n + c.sum
  }

  class LibClassVarargs(i: Int)(v: Int*) extends LibTraitVarargs

  object LibObjectVarargs extends LibTraitVarargs
}
