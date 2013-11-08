package t1001272

class A(a: Int) {
  def this() = this(123)

  class InnerA(i: Int)
}

class B extends A(1)

object D {
  class E(i: Int)
}

object Test {
  val a = new A( /*!*/ )
  val b = new B( /*!*/ )
  val e = new D.E( /*!*/ )
  val ia = new a.InnerA( /*!*/ )
}
