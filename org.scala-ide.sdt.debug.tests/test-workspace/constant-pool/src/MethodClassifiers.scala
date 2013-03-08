package stepping

class Defaults(someArg: String = "a default String") {

  def methWithDefaults(someArg1: String = "another default String") = {}
  def methWithDefaults2(arg1: String, arg2: Int = 42)(barg1: String, barg2: Int = arg2 + 1) = {}
}

trait BaseTrait {
  def concreteTraitMethod1(x: Int) = x
  def concreteTraitMethod2(x: Int, y: Int): Boolean = false
  def concreteTraitMethod3(x: Int, y: Long, z: String): Long = y
  def concreteTraitMethod4(x: Int, y: Double, z: String, t: Object): Unit = ()

  private def private_concreteTraitMethod1(x: Int) = x
  private def private_concreteTraitMethod2(x: Int, y: Int): Boolean = false
  private def private_concreteTraitMethod3(x: Int, y: Long, z: String): Long = y
  private def private_concreteTraitMethod4(x: Int, y: Double, z: String, t: Object): Unit = ()


  def concreteTraitMethodWithDefault(someArg2: String = "yet another default String") = {
    someArg2
  }

  def abstractMethodWithDefault(someArg3: String = "last default String")

  val concreteField1: Int = 42
  val abstractField1: String
  var concreteMField1: Int = 20
  var abstractMField1: String

}

class ConcreteClass extends BaseTrait {
  def abstractMethodWithDefault(someArg3: String) = ""

  // static call to a Java method, but not a forwarder!
  def console() {
    System.console()
  }

  val abstractField1: String = "f1"
  var abstractMField1: String = "f2"

  private var fakePrivate: String = "fakePrivate"

  class Inner {
    fakePrivate
  }
}


class MethodClassifiers {

  def mainTest() {
    val d = new Defaults()

    d.methWithDefaults()

    val c = new ConcreteClass

    c.concreteTraitMethod1(42)
    c.concreteTraitMethodWithDefault()
    c.abstractMethodWithDefault()
  }
}