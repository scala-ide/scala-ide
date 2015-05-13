package debug

object NamedParameters extends App {

  def method(i: Int, s: String) = s + i

  def varargMethod(i: Int, s: String, ss: String*) = s + i + ss.mkString

  def defaultArgMethod(s: String = "Ala", a: Int = 1, b: Int = 2, c: Int = 3) = s + a + b + c

  def foo() {
    // breakpoint here
    val breakpoint = ???
  }

  foo()
}
