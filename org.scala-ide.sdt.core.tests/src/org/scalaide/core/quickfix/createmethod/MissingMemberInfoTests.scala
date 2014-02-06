package scala.tools.eclipse.quickfix.createmethod

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.util.parsing.ScalariformParser

import org.junit.Assert._
import org.junit.Test

object MissingMemberInfoTests extends TestProjectSetup("quickfix") {
  private lazy val unit = compilationUnit("createmethod/CreateMethod.scala").asInstanceOf[ScalaCompilationUnit]
  private lazy val source = new String(unit.getContents)
  private lazy val ast = ScalariformParser.safeParse(source).get._1
}

class MissingMemberInfoTests {
  import MissingMemberInfoTests._

  @Test def function2BecomesFunction1() {
    val (parameterList, returnType) = infer("other.higher4")
    assertEquals(List(List(("arg", "String => Double"))), parameterList)
    assertEquals(Some("Int => Char"), returnType)
  }

  @Test def hofWithFreeReturnType() {
    val (parameterList, returnType) = infer("other.higher1")
    assertEquals(List(List(("arg", "Int"))), parameterList)
    assertEquals(None, returnType)
  }

  @Test def hofCalledViaNamedParameter() {
    val (parameterList, returnType) = infer("other.higher5")
    assertEquals(List(List(("arg", "Double"))), parameterList)
    assertEquals(Some("String"), returnType)
  }

  private def infer(callee: String) = {
    val index = source.indexOf(callee)
    assertNotSame(-1, index)
    MissingMemberInfo.inferFromEnclosingMethod(unit, ast, index)
  }
}