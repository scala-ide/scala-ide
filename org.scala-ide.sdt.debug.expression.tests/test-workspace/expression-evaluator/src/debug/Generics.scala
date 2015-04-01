package debug

trait GenericTrait[C]

class GenericClass[A, C] extends GenericTrait[C] {

  def foo[B](a: A, b: B) = {
    // breakpoint here, keep in sync with TestValues
    val bp = 1
  }
}

object Generics extends TestApp {
  new GenericClass[Int, String]().foo[String](1, "ala")
}