package scala.tools.eclipse
package pc

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.util.Logger
import scala.tools.nsc.interactive.InteractiveReporter
import org.eclipse.jdt.core.ICompilationUnit
import org.junit.Assert._
import org.junit._
import org.mockito.Matchers._
import org.mockito.Mockito._
import scala.tools.eclipse.hyperlinks.HyperlinkTester
import scala.tools.eclipse.testsetup.CustomAssertion

object PresentationCompilerTest extends testsetup.TestProjectSetup("pc") with CustomAssertion with HyperlinkTester

class PresentationCompilerTest {
  import PresentationCompilerTest._

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
              override val logger = mockLogger
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
    verify(mockLogger, times(0)).error(any(), any())
  }

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

  @Ignore("Enable test when ticket #1000658 is fixed")
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
}
