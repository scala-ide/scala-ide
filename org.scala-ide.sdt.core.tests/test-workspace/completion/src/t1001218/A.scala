package t1001218

object A {
  def foo(): Int = 123
  def foooo(): Int = 123
  def bar(): Int = 123
}

class B {
  println( /*!*/ )

  A.foo( /*!*/ )

  A foo( /*!*/ )
}

class C {
  import A._

  foo( /*!*/ )
}
