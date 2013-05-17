class Bar {
  val f = new Foo
  f.obj1/*ref*/ = new Object

  val bar = f.obj1
  def bar2 = f.obj1
}