package debug

object NewInstances extends App {

  def foo(): Unit = {
    implicit val implInt = 12

    "breakpoint"
  }

  foo()
}