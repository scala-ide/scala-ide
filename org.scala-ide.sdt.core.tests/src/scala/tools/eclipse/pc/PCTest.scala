package scala.tools.eclipse
package pc

import org.junit._
import Assert._
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.eclipse.jdt.core.{ IProblemRequestor, WorkingCopyOwner, ICompilationUnit }
import org.eclipse.core.runtime.NullProgressMonitor
import scala.tools.nsc.interactive.InteractiveReporter
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.nsc.io.AbstractFile
import scala.tools.eclipse.util.Logger

object PCTest extends testsetup.TestProjectSetup("pc") {
  private def triggerStructureBuilderFor(unit: ICompilationUnit) {
    val requestor = mock(classOf[IProblemRequestor])
    // the requestor must be active, or unit.getWorkingCopy won't trigger the Scala
    // structure builder
    when(requestor.isActive()).thenReturn(true)

    val owner = new WorkingCopyOwner() {
      override def getProblemRequestor(unit: org.eclipse.jdt.core.ICompilationUnit): IProblemRequestor = requestor
    }

    // this will trigger the Scala structure builder
    unit.getWorkingCopy(owner, new NullProgressMonitor)
  }
}

class PCTest {
  import PCTest._
  
  @Test
  def creatingOverrideIndicator_ShouldNotReportError_t1000531() {
    // when
    val unit = compilationUnit("t1000531/A.scala")
    triggerStructureBuilderFor(unit)
    val mockLogger = mock(classOf[Logger])
    
    // then
    val scu = unit.asInstanceOf[ScalaCompilationUnit]
    project.withSourceFile(scu) { (sourceFile, compiler) =>
      try {
        compiler.withStructure(sourceFile, keepLoaded = true) { tree =>
          compiler.askOption { () =>
            val overrideIndicatorBuilder = new compiler.OverrideIndicatorBuilderTraverser(scu, new java.util.HashMap) {
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
}