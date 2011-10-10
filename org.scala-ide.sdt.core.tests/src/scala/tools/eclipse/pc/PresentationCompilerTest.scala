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
import scala.tools.eclipse.testsetup.CustomAssertion.assertNoErrors
import scala.tools.eclipse.testsetup.SDTTestUtils.{triggerScalaStructureBuilderFor, waitUntilTypechecked}

object PresentationCompilerTest extends testsetup.TestProjectSetup("pc")

class PresentationCompilerTest {
  import PresentationCompilerTest._

  @Test
  def creatingOverrideIndicator_ShouldNotReportError_t1000531() {
    // when
    val unit = compilationUnit("t1000531/A.scala")
    triggerScalaStructureBuilderFor(unit)
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
    val packageUnit = compilationUnit("t1000647/foo/package.scala")
    triggerScalaStructureBuilderFor(packageUnit)
    reload(packageUnit)

    // then
    val dataFlowUnit = compilationUnit("t1000647/bar/DataFlow.scala")
    triggerScalaStructureBuilderFor(dataFlowUnit)
    reload(dataFlowUnit)
    
    // give a chance to the background compiler to report the error
    waitUntilTypechecked(project, dataFlowUnit)

    // verify
    assertNoErrors(project, dataFlowUnit)
  }

  @Ignore("Enable test when ticket is fixed")
  @Test
  def illegalCyclicReferenceInvolvingObject_t1000658() {
    //when
    val unit = compilationUnit("t1000658/ThreadPoolConfig.scala")
    //then
    reload(unit)
    // verify
    assertNoErrors(project, unit)
  }
}