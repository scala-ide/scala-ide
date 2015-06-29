package valfinding

class BaseClass(baseParam: String) {
  val baseField /*{base class field decl}*/ = "baseField"

  def baseFunc(bfParam /*{base class method param}*/: String): Unit = {
    baseField /*{base class field usage}*/
  }
}

class DerivedClass(derivedParam: Int) extends BaseClass("baseParamFromDerived") /*with TheTrait*/ {
  val derivedField = "derived field"

  baseFunc("base meth param")
  derivedFunc("dfParam")

  def derivedFunc(dfParam: String): Unit = {
    baseField /*{base class field usage from derived class}*/
    derivedField /*{derived class field usage}*/
  }
}

trait TheTrait {
  val traitField /*{trait field decl}*/ = "traitFieldd"
  private val privateTraitField = "privateTraitField"

  def traitFunc(tfParam /*{trait method param}*/: String = "traitFuncParam"): Unit = {
    traitField /*{trait field usage from trait}*/
    privateTraitField /*{private trait field usage from trait}*/
  }
}

class ExplicitExtenderOfTheTrait extends TheTrait {
  traitField /*{trait field access from ctor of extender}*/

  def fun: Unit = {
    traitField /*{trait field access from method of extender}*/
    traitFunc()
  }
}