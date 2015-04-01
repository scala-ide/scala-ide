package debug

object Varargs extends TestApp {

  object vararg {
    def f(ss: String*): String = "s*"
    def g(i: Int, ss: String*): String = "i,s*"
    def h(s: String, i: Int, ss: String*): String = "s,i,s*"

    def f_+(ss: String*): String = "s*"
    def g_+(i: Int, ss: String*): String = "i,s*"
  }

  object sameErasedSignature {
    def f(is: Int*): Int = is.sum
    def f(ss: String*): String = ss.mkString
  }

  object argumentAndVarArg {
    def f(s: String): String = "s"
    def f(s: String, ss: String*): String = "s,s*"
  }

  object varargWithSimpleOverloads {
    def f(): String = ""
    def f(s1: String): String = "s"
    def f(ss: String*): String = "s*"
  }

  class A
  class B extends A

  object varargsAndSubtyping {
    def f(as: A*): Int = 1
    def f(bs: B*): String = "2"
  }

  object varargsAndPrimitiveCoercion {
    def f(is: Int*): Int = is.sum
    def f(ds: Double*): Double = ds.sum
  }

  def foo() {
    // number of bottom line must be specified in org.scalaide.debug.internal.expression.integration.TestValues object because a lot of tests use this line
    val debug = ???
  }

  foo()
}
