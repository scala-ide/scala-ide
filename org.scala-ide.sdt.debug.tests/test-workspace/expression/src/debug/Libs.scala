package debug

object Libs {

  def libMultipleParamers(a: Int)(b: Int) = a + b

  def function(a: Int) = a + 1

  val value = 11

  override def toString: String = "Libs - object"
}

class LibClassWithoutArgs {
  override def toString: String = "LibClassWithoutArgs"
}

case class LibClass(a: Int) {

  private var i = a

  def perform[A](func: Int => A) = func(a)

  def performByName(func: => Int) = func + a

  def performByNameGen[A](func: => A) = func

  def performTwice(func: => Int) = func + func

  def incrementAndGet() = {
    i += 1
    i
  }

  private implicit val implInt = 3

  def withImplicitConversion(param: ImplicitLibClass) = param.value

  def withImplicitParameter(implicit value: ImplicitLibClass) = value.value

  def withImplicitIntParameter(implicit value: Int) = value

  def withImplicitStringParameter(implicit value: String) = value

  def withImplicitDoubleParameter(implicit value: Double) = value
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