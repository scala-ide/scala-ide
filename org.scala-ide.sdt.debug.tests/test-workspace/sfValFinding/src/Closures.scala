package valfinding

class ClosureTest {
  val fieldC = "captured field"

  def useClosures() {
    val localValC = "Local val captured"
    val shadowedInClosure = "This shouldn't be shown"

    List("clParam1", "clParam2", "clParam3").map {closureParam /*{closure param decl}*/ =>
      closureParam /*{closure param usage}*/
      fieldC /*{captured field of enclosing class}*/
      localValC /*{captured local variable of enclosing method}*/

      val shadowedInClosure /*{local var of closure shadowing local var of enclosing method}*/ = "shadowed in closure"
      shadowedInClosure
    }
  }

  def localSimilar {
    val closureParam /*{local of another method named similarly to a local of closure}*/ = "SSS"
  }
}