package valfinding

trait EnclosingTrait {
  val traitField = "tField"

  new Nestedd

  class Nestedd {
    val nestedField /*{field decl of class nested in trait}*/ = "nField"
    nestedField /*{field usage of class nested in trait}*/
    traitField /*{field of enclosing trait usage}*/
  }
}