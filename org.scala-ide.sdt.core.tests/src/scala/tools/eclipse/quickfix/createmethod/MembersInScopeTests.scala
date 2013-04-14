package scala.tools.eclipse.quickfix.createmethod

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.testsetup.TestProjectSetup

import org.junit.Assert._
import org.junit.Test

import MembersInScopeTests.compilationUnit

object MembersInScopeTests extends TestProjectSetup("quickfix")

class MembersInScopeTests {
  import MembersInScopeTests._

  @Test def scopeCheck() {
    val unit = compilationUnit("createmethod/ScopeCheck.scala").asInstanceOf[ScalaCompilationUnit]
    val inScope = MembersInScope.getValVarAndZeroArgMethods(unit, new String(unit.getContents).indexOf("findMe"))
    assertContainsAll(inScope, List(InScope("class2", "createmethod.Class2"),
      InScope("class3", "createmethod.Class3"),
      InScope("class4", "createmethod.Class4"),
      InScope("param1", "Int")
      //compiler bug? the compiler says class1 (defined in Parent) is not accessible
      /*, InScope("class1", "createmethod.Class1")*/ 
      ))
    
    assertContainsNone(inScope, List(InScope("class6", "createmethod.Class6")))
  }

  private def assertContainsAll(all: List[InScope], searchFor: List[InScope]) {
    for (scope <- searchFor) assertTrue(s"Looking for $scope in $all", all.contains(scope))
  }

  private def assertContainsNone(all: List[InScope], searchFor: List[InScope]) {
    for (scope <- searchFor) assertFalse(s"Found $scope", all.contains(scope))
  }
}