abstract class Foo {
  val obj/*ref*/ : Object

  def foo(): Unit = {
    obj.toString
  }
}