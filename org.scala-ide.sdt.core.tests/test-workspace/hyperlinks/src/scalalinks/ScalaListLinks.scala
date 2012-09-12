package scalalinks

class Foo {
  def bar {
    val xs = List/*^*/(1, 2, 3)
    List/*^*/
    Seq/*^*/()
    Seq/*^*/
    Nil/*^*/
    xs/*^*/(1)
  }
}