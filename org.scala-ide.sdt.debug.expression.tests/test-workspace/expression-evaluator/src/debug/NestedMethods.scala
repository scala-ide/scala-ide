package debug

object NestedMethods extends TestApp {

  def foo() {
    val closureParam = "closureParam"

    def simpleNested(i: Int) = s"simpleNested: $i"

    def nestedTwice(i: Int) = {
      def nestedTwice(j: Int) = "Error: inner function instead of outer one"
      nestedTwice(i).mkString
      "nestedTwice"
    }

    def nestedInMultipleMethods(i: Int) = "nestedInMultipleMethods"

    def nestedStringMethod(s: String) = "nestedStringMethod"

    def nestedDefinedInLambda(i: Int) = "Error: nestedInLambda from this class"

    def nestedWithClosure(i: Int) ={
      closureParam.mkString
      "nestedWithClosure"
    }

    def nestedMethodWithoutParenthesis = "nestedMethodWithoutParenthesis"

    def multipleParametersNestedMethod(i: Int)(j: Int) = "multipleParametersNestedMethod"

    def multipleParametersNestedMethodReturningFunction(i: Int)(j: Int): Int => String = _ => "multipleParametersNestedMethodReturningFunction"

    def nestedWithExistentialType(list: List[_]) = "nestedWithExistentialType"

    val list = List(1)
    list.map { number =>
      def nestedDefinedInLambda(i: Int) = "nestedDefinedInLambda"

      val nestedFunction: Int => String = _ => "nestedFunction"

      // number of bottom line must be specified in
      // org.scalaide.debug.internal.expression.integration.TestValues.NestedMethodsTestCase.breakpointLine
      nestedStringMethod("a").mkString
      nestedDefinedInLambda(1)
      nestedFunction(1)
    }

    def declaredAfterBreakpoint(i: Int) = "Error: it should not be visible"

    nestedDefinedInLambda(1)
    nestedTwice(1)
    nestedInMultipleMethods(1)
    declaredAfterBreakpoint(1)
    nestedWithClosure(1)
    simpleNested(1)
    nestedMethodWithoutParenthesis
  }

  foo()

  val objectList = List(1,2)

  def foo2() {
    def nestedInMultipleMethods(i: Int) = "Error: nestedInMultipleMethods from different function"
    nestedInMultipleMethods(1)
  }
}