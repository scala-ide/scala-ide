package bug1000560

class Foo {
  def foo() = 2
  def bar = 2
}

object Test2 {
  val foo = new Foo
  import foo/*^*/.{bar/*^*/ => _, _}
}