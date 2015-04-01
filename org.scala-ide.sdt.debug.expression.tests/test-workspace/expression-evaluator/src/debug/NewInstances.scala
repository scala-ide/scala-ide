package debug

trait A
class B

case class Foo()
case class Bar(foo: Foo)
case class Baz(foo: Bar)

object NewInstances extends TestApp {

  def foo(): Unit = {
    implicit val implInt = 12
    val foo = new Foo()
    val bp = "breakpoint"
  }

  foo()
}