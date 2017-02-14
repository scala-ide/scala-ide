package test {
  package b {
    class B {
      object OB {
        def obFoo(i: Int)(f: Int => Int) =
          f(i)
      }
      class BB {
        def bbFoo =
          OB.obFoo(5) { x =>
            x + 42
          }
      }
      def foo =
        (new BB).bbFoo
    }
    package c {
      trait C {
        def bar = 7
      }
    }
  }
  package a {
    import test.b.c.C
    object Main extends App with C {
      import test.b.B
      val b = new B
      println(b.foo)
      println(bar)
    }
  }
}
