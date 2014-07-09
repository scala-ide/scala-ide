package valfinding

class ScalaClass(nonFieldClassParamOnlyUsedInCtor /*{non-field class param only used in ctor (decl)}*/ : String, private var fieldClassParam /*{class param & field decl}*/ : String) {
  fieldClassParam /*{class param & field usage}*/
  nonFieldClassParamOnlyUsedInCtor /*{non-field class param only used in ctor (usage)}*/

  val classField /*{class field decl}*/ = "classField"
  classField /*{class field usage}*/

  val cc = CaseC("Case")
  cc.classField /*{field with same name of a field}*/  // Shouldn't find a value.

  func("func param")

  def func(funcParam /*{method param decl}*/: String) = {
    funcParam /*{method param usage}*/
    val localVall /*{method-local variable}*/ = "local val"

    nested1

    def nested1 {
      val nested1Local = "nested1Local"
      nested2("nesteds parameter")

      def nested2(nestedMethodParam /*{nested method param}*/: String) {val nested2Local /*{nested method local}*/ = "nested2Local"
        nested1Local /*{enclosing nested method local}*/
        localVall /*{root enclosing method local}*/
      }
    }
  }

  def someMethodWeWillNeverStepInto(funcParam /*{similarly named param of a method we are not in}*/: String = "similarly named") {
    val localVall /*{similarly named local var of a method we are not in}*/ = "Similarly Named"
  }
}

class ClassIntoWhichWeWillNeverStep(var fieldClassParam /*{similarly named field decl of a class we are not in}*/: String) {
  fieldClassParam /*{similarly named field usage of a class we are not in}*/
}

case class CaseC(classField: String) {
  val f2: String = classField + " field2"
}
