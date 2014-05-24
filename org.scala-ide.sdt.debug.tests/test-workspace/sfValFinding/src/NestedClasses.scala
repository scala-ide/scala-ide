package valfinding

class OuterClass {
  val outerExclusiveField = "outerExclusiveField"
  val fff = "Outer's Field"

  class InnerClass {
    val fff /*{inner class field decl}*/ = "Inner's Field"

    testFields

    def testFields {
      val fff = "Local Shadower"

      fff /*{method-local var shadowing field}*/                   // Should show "Local Shadower"
      this.fff /*{shadowed field accessed with this}*/              // Should show "Inner's field"
      InnerClass.this.fff /*{shadowed field accessed with this with class name}*/    // Should show "Inner's field"
      OuterClass.this.fff /*{shadowed field accessed with this with enclosing class name}*/   // Should show "Outer's field"

      outerExclusiveField /*{exclusive field of enclosing class}*/
    }
  }
}