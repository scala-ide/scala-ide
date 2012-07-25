package abstract_members

class FooImpl extends Foo {
  override val obj1: Any = new Object
  override val obj2: AnyRef = new Object
  override def obj2_=(that: AnyRef): Unit = {}
  override def obj3: Foo = this
  override def obj4(s: String): String = s
}