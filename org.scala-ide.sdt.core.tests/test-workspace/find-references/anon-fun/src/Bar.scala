object Bar {
  val f = List(new Foo {})
  f.foreach(_.foo())
}