class Foo {
  lazy val lazyX/*ref*/ = 0
}

class Bar {
  def meth {
    val obj = new Foo
    obj.lazyX
  }
}