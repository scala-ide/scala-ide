class Foo {
  lazy val x/*ref*/ = 0
}

class Bar {
  def meth {
    val obj = new Foo
    obj.x
  }
}