package scala.tools.eclipse
package pc

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.logging.Logger
import scala.tools.nsc.interactive.InteractiveReporter
import org.eclipse.jdt.core.ICompilationUnit
import org.junit.Assert._
import org.junit._
import org.mockito.Matchers._
import org.mockito.Mockito._
import scala.tools.eclipse.hyperlink.HyperlinkTester
import scala.tools.eclipse.testsetup.CustomAssertion

object PresentationCompilerTest extends testsetup.TestProjectSetup("pc") with CustomAssertion with HyperlinkTester

class PresentationCompilerTest {
  import PresentationCompilerTest._

  @Before
  def reset() {
    project.resetPresentationCompiler()
  }

  @Test
  def creatingOverrideIndicator_ShouldNotReportError_t1000531() {
    // when
    val unit = open("t1000531/A.scala")
    val mockLogger = mock(classOf[Logger])

    // then
    project.withSourceFile(unit) { (sourceFile, compiler) =>
      try {
        compiler.withStructure(sourceFile, keepLoaded = true) { tree =>
          compiler.askOption { () =>
            val overrideIndicatorBuilder = new compiler.OverrideIndicatorBuilderTraverser(unit, new java.util.HashMap) {
              override val eclipseLog = mockLogger
            }
            // if the unit is not kept loaded (i.e., `keepLoaded = false`), then a message 
            // "Error creating override indicators" is reported. That is why this test checks
            // that no error is reported to the mocked logger.
            overrideIndicatorBuilder.traverse(tree)
          }
        }
      }
    }()

    // verify
    verify(mockLogger, times(0)).error(any())
  }

  @Test
  def freshFileReportsErrors() {
    val contentsWithErrors = """
package t1001094

class FreshFile {
  val x: ListBuffer[Int] = null
}"""
    val unit = open("t1001094/FreshFile.scala")
    val errors = unit.currentProblems()
    Assert.assertEquals("Unexpected errors", errors, Nil)

    unit.getBuffer().setContents(contentsWithErrors)
    SDTTestUtils.waitUntil(5000)(unit.currentProblems ne Nil)
    val errors1 = unit.currentProblems()
    Assert.assertNotSame("Unexpected clean source", errors1, Nil)
  }

  @Ignore("Enable this once we understand why it spuriously fail #1001588")
  @Test
  def implicitConversionFromPackageObjectShouldBeInScope_t1000647() {
    //when
    open("t1000647/foo/package.scala")

    // then
    val dataFlowUnit = open("t1000647/bar/DataFlow.scala")
    
    // give a chance to the background compiler to report the error
    waitUntilTypechecked(dataFlowUnit)

    // verify
    assertNoErrors(dataFlowUnit)
  }

  @Test
  def illegalCyclicReferenceInvolvingObject_t1000658() {
    //when
    val unit = scalaCompilationUnit("t1000658/ThreadPoolConfig.scala")
    //then
    reload(unit)
    // verify
    assertNoErrors(unit)
  }
  
  @Ignore("Ticket #1000692 is fixed (at least it looks like it is working). However this test it is still failing. "+
      "We decided to look at it and understand why it is not passing only after 2.0 release.")
  @Test
  def notEnoughArgumentsForCconstructorError_ShouldNotBeReported_t1000692() {
    //when
    val unit = scalaCompilationUnit("t1000692/akka/util/ReflectiveAccess.scala")
    val oracle = List(Link("class t1000692.akka.config.ModuleNotAvailableException"))
    //then
    // it is important to ask hyperlinking before reloading!
    loadTestUnit(unit).andCheckAgainst(oracle) 
    reload(unit)
    // verify
    assertNoErrors(unit)
  }
  
  @Test
  def psShouldReportTheCorrectCompilationUnitsItKnowsAbout() {
    def managedUnits() = project.withPresentationCompiler(_.compilationUnits)()
    
    project.shutDownCompilers()
    
    // should be empty
    Assert.assertTrue("Presentation compiler should not maintain any units after a shutdown request", managedUnits().isEmpty)
    
    val cu = scalaCompilationUnit("t1000692/akka/util/ReflectiveAccess.scala")
    
    // still no units should be loaded
    Assert.assertTrue("Presentation compiler should not maintain any units after structure build (%s)".format(managedUnits()), managedUnits().isEmpty)
    
    cu.scheduleReconcile().get

    // now the unit should be managed
    Assert.assertEquals("Presentation compiler should maintain one unit after reload (%s)".format(managedUnits()), 1, managedUnits().size)
  }
  
  @Test
  @Ignore("Enable this test once #1000976 is fixed")
  def correctlyTypecheckClassesWithDefaultArguments_t1000976() {
    def openUnitAndTypecheck(path: String): ScalaSourceFile = {
      val unit = scalaCompilationUnit(path).asInstanceOf[ScalaSourceFile]
      unit.reload()
      waitUntilTypechecked(unit)
      unit
    }

    // SUT: Opening A.scala w/ full typecheck and then B.scala determines the "ghost error" to show up 
    openUnitAndTypecheck("t1000976/a/A.scala")
    val unitB = openUnitAndTypecheck("t1000976/b/B.scala")

    // verify
    assertNoErrors(unitB)
  }

  @Test
  def libraryDocumentation(): Unit =
    project.withPresentationCompiler { compiler =>
      import compiler.{ reload => _, parseAndEnter => _, _ }
      import definitions.ListClass
      val unit = ask { () => findCompilationUnit(ListClass).get }
      reload(unit)
      parseAndEnter(unit)
      unit.doWithSourceFile { (source, _) =>
        val documented = ask { () =>
          // Only check if doc comment is present in the class itself.
          // This doesn't include symbols that are inherited from documented symbols.
          // An alternative would be to check allOverriddenSymbols, but
          // that would require getting sourceFiles for those as well. I'm a bit lazy :)
          ListClass.info.decls filter { sym =>
            unitOf(source).body exists {
              case DocDef(_, defn: DefTree) if defn.name eq sym.name => true
              case _ => false
            }
          }
        }
        Assert.assertTrue("Couldn't find documented declarations", documented.nonEmpty)
        for (sym <- documented) {
           Assert.assertTrue(s"Couldn't retrieve $sym documentation",
                             parsedDocComment(sym, sym.enclClass).isDefined)
        }
      }
    } {
      Assert.fail("shouldn't happen")
    }

}

