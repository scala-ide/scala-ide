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

  private implicit val implInt = 3

  def withImplicitConversion(param: ImplicitLibClass) = param.value

  def withImplicitParameter(implicit value: ImplicitLibClass) = value.value

  def withImplicitIntParameter(implicit value: Int) = value

  def withImplicitStringParameter(implicit value: String) = value

  def withImplicitDoubleParameter(implicit value: Double) = value

  def withDefaultValue(name: String = "ala") = name

  def withExplicitAndDefaultValue(prefix: String, name: String = "ala") = s"$prefix $name"

  def withNamedParameter(left: Boolean = true, top: Boolean = true) = s"left $left top $top"
}

case class LibClass2Lists(a: Int)(b: Int)

case class LibClassImplicits(a: Int)(implicit b: ImplicitLibClass)

case class LibClass2ListsAndImplicits(a: Int)(b: Int)(implicit c: ImplicitLibClass)

case class LibClassWithVararg(a: Int*)

case class ImplicitLibClass(val value: Int)

object ImplicitLibClass {
  implicit val defult: ImplicitLibClass = new ImplicitLibClass(1)

  implicit def int2ImplcitLibClass(value: Int) = new ImplicitLibClass(value)
}

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