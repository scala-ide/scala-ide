package test {
  package b {
    class B {
      def foo = 5
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
