package abstract_members

abstract class Foo {
  val obj1: Any
  var obj2: AnyRef
  def obj3: Foo
  def obj4(s: String): String
}