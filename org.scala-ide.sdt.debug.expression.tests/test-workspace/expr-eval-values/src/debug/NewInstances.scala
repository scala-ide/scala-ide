package debug

trait A
class B

object NewInstances extends App {

  def foo(): Unit = {
    implicit val implInt = 12

    val bp = "breakpoint"
  }

  foo()
}