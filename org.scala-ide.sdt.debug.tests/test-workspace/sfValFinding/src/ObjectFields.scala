package valfinding

object Objectt {
  val field /*{object field decl}*/ = "obj field"

  def f(param: String): Unit = {
    field  /*{object field usage}*/
  }
}