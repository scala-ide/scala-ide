package t1000678_2

class A {

  class B {
    def foo: Unit = {}
  }

  case class C {
    def foo: Unit = {}
    class D {
      def foo: Unit = {}
    }
  }
}
