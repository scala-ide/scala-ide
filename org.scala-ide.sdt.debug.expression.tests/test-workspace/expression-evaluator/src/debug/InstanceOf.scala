package debug

trait A1
class A2 extends A1
object A3 extends A2

trait B1
class B2 extends B1
class B3 extends B2

trait C1
trait C2 extends C1
class C3 extends C2

trait D1
trait D2
class D3 extends D2 with D1

class FooInstance
class BarInstance extends FooInstance

object InstanceOf extends TestApp {

  def check[A](a: A): A = a

  def foo() {
    def byte: Any = (4: Byte)
    def short: Any = (6: Short)
    def int: Any = 1
    def long: Any = 1l
    def char: Any = 'c'
    def double: Any = 1.1
    def float: Any = 1.1f
    def boolean: Any = false
    def string: Any = "Ala"
    def intList: Any = List(1, 2, 3)
    def stringList: Any = List("a", "b", "c")

    def unit: Any = ()
    def nullVal: Any = null

    def intArray: Any = Array(1, 2, 3)
    def doubleArray: Any = Array(1.0, 2.0, 3.0)
    def objectArray: Any = Array("a", "b", "c")

    def fooArray: Any = Array(new FooInstance)
    def barArray: Any = Array(new BarInstance)

    def A1: Any = new A1 {}
    def A2: Any = new A2
    def B2: Any = new B2
    def B3: Any = new B3
    def C2: Any = new C2 {}
    def C3: Any = new C3
    def D3: Any = new D3

    // number of bottom line must be specified in org.scalaide.debug.internal.expression.integration.TestValues object because a lot of tests use this line
    val debug = ???
  }

  foo() //this line number appears in AppObject test
}
