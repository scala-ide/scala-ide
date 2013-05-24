package scala.tools.eclipse.quickfix.createmethod

import scala.tools.eclipse.quickfix.QuickFixesTests

import org.junit.Test

class CreateMethodTests {
  import QuickFixesTests._
   
    @Test def createMethod {
    withQuickFixes("createmethod/CreateMethod.scala")(
        "Create method 'easy1(String, Int)' in type 'OtherClass'",
        "Create method 'easy2(OtherClass)'",
        "Create method 'multiple1(Int)(OtherClass, Double)' in type 'OtherClass'",
        "Create method 'multiple2(Any)(AnyRef)'",
        "Create method 'named1(String, Int)' in type 'OtherClass'",
        "Create method 'expected1(): Int' in type 'OtherClass'",
        "Create method 'expected2(): String' in type 'OtherClass'",
        "Create method 'higher1(Int)' in type 'OtherClass'",
        "Create method 'higher2(Int, Int): Boolean' in type 'OtherClass'",
        "Create method 'higher3(Int, Int): Boolean'",
        "Create method 'higher4(String => Double): Int => Char' in type 'OtherClass'",
        "Create method 'higher5(Double): String' in type 'OtherClass'",
        "Create method 'higher6(Double): String'",
        "Create method 'unary_!(): OtherClass' in type 'OtherClass'",
        "Create method 'unary_-(): OtherClass' in type 'OtherClass'",
        "Create method 'infix1(String)' in type 'OtherClass'",
        "Create method 'infix2(List[Int])' in type 'OtherClass'",
        "Create method 'infix3(String)' in type 'OtherClass'",
        "Create method 'infix4(String)' in type 'OtherClass'",
        "Create method 'infix5(String, Int, OtherClass)' in type 'OtherClass'",
        "Create method 'infix6(String, Int, OtherClass, Double)' in type 'OtherClass'",
        "Create method 'namedinfix1(Int, OtherClass, String)' in type 'OtherClass'",
        "Create method 'namedinfix2(String)(Int, OtherClass)(String)' in type 'OtherClass'",
        "Create method 'selfinfix1(Int, String)'",
        "Create method 'selfinfix2(Int, String)(OtherClass, String, Int)'",
        "Create method 'compound1()' in type 'OtherClass'",
        "Create method 'complex1(List[Double])' in type 'OtherClass'",
        "Create method 'complex2(List[Int])' in type 'OtherClass'",
        "Create method 'complex3(List[Int], List[List[Int]])(Boolean)' in type 'OtherClass'",
        "Create method 'complex4(Any)' in type 'OtherClass'",
        "Create method 'complex5(Any)' in type 'OtherClass'",
        "Create method 'complex6(Any)' in type 'OtherClass'"
    )
  }
  
}
