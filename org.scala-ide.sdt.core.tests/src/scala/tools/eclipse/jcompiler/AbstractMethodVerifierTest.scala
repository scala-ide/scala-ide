package scala.tools.eclipse.jcompiler

import org.junit.Assert._
import org.junit.Test
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.text.source.IAnnotationModelListener
import org.eclipse.jface.text.source.IAnnotationModel
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.internal.ui.viewsupport.IProblemChangedListener
import org.eclipse.core.resources.IResource
import org.eclipse.jdt.core.IProblemRequestor
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.core.resources.IMarker
import org.eclipse.jdt.core.IJavaModelMarker
import org.mockito.Mockito._
import org.mockito.Matchers.any
import org.junit.Ignore
import org.mockito.verification.VerificationMode

object AbstractMethodVerifierTest extends TestProjectSetup("jcompiler") {

  private def whenOpening(path2unit: String) = new {
    def verifyThat(mode: VerificationMode) = new {
      def error = errors
      def errors = new {
        def is = are
        def are = new {
          def reported = {
            //when
            val unit = compilationUnit(path2unit)

            val requestor = mock(classOf[IProblemRequestor])
            when(requestor.isActive()).thenReturn(true)

            val owner = mock(classOf[WorkingCopyOwner])
            when(owner.getProblemRequestor(any())).thenReturn(requestor)

            //then
            // this will trigger the java reconciler so that the problems will be reported in the ProblemReporter
            unit.getWorkingCopy(owner, new NullProgressMonitor)

            // verify
            verify(requestor, mode).acceptProblem(any())
          }
        }
      }
    }

    def verifyThat(expectedProblem: String) = new {
      def is = new {
        def reported = {
          //when
          val unit = compilationUnit(path2unit)
          
          val owner = new WorkingCopyOwner() {
            override def getProblemRequestor(unit: ICompilationUnit): IProblemRequestor = {
              new IProblemRequestor {
                override def acceptProblem(problem: IProblem) {
                  //verify
                  assertEquals(expectedProblem, problem.toString())
                }
                def beginReporting() {}
                def endReporting() {}
                def isActive(): Boolean = true
              }
            }
          }
          
          //then
          // this will trigger the java reconciler so that the problems will be reported in the ProblemReporter
          unit.getWorkingCopy(owner, new NullProgressMonitor)
        }
      }
    }
  }

  private def no: VerificationMode = never()
  private def one: VerificationMode = times(1)
}

class AbstractMethodVerifierTest {
  import AbstractMethodVerifierTest._

  @Test
  def javaClassExtendingScalaClassWithConcreteMethodsInSuperTrait_NoErrorIsReportedInJavaEditor_t1000594_pos() {
    whenOpening("t1000594_pos/C.java").verifyThat(no).errors.are.reported
  }

  @Test
  def javaClassExtendingScalaClassWithDeferredMethodsInSuperTrait_ErrorsAreReportedInJavaEditor_t1000594_neg() {
    whenOpening("t1000594_neg/C.java").verifyThat(one).error.is.reported
  }

  @Test
  def javaClassExtendingScalaClassWithDeferredMethodsInSuperTrait_ErrorsAreReportedInJavaEditor_t1000607() {
    whenOpening("t1000607/C.java").verifyThat(one).error.is.reported
  }

  @Test
  def javaClassExtendingPureScalaInterface_JavaEditorShouldReportErrorsForUnimplementedDeferredMethod_t1000670() {
    whenOpening("t1000670/JFoo.java").verifyThat(one).error.is.reported
  }

  @Test
  def javaClassExtendingAbstractScalaClassWithMixedDeferredAndConcreteMembersWithSameSignature_JavaEditorShouldNotReportErrorsForUnimplementedDeferredMethod_t1000670_1() {
    whenOpening("t1000670_1/JFoo.java").verifyThat(no).errors.are.reported
  }

  @Test
  def javaClassExtendingAbstractScalaClassWithMixedDeferredAndConcreteMembersWithSameSignature_JavaEditorShouldNotReportErrorsForUnimplementedDeferredMethod_t1000670_2() {
    whenOpening("t1000670_2/JFoo.java").verifyThat(no).errors.are.reported
  }

  @Test
  def scalaMethodVerifierProvider_isNotExecutedOnJavaSources_t1000660() {
    val expectedProblem = "Pb(400) The type ScalaTest must implement the inherited abstract method Runnable.run()"

    whenOpening("t1000660/ScalaTest.java").verifyThat(expectedProblem).is.reported
  }
  
  @Test
  def t1000741() {
    whenOpening("t1000741/FooImpl.java").verifyThat(no).errors.are.reported
  }
    
  @Test
  def protectedScalaEntitiesNeedToBeExposedToJDTAsPublic_t1000751() {
    whenOpening("t1000751/J.java").verifyThat(no).errors.are.reported
  }
  
  @Test
  def scalaAnyRefNeedToBeMappedIntoJavaLangObject_ToBeCorrectlyExposedToJDT_1000752() {
    whenOpening("t1000752/J.java").verifyThat(no).errors.are.reported
  }
  
  @Test
  def scalaArrayNeedToBeMappedIntoJavaArray_ToBeCorrectlyExposedToJDT_t1000752_1() {
    whenOpening("t1000752_1/J.java").verifyThat(no).errors.are.reported
  }
  
  @Test
  def simplifiedExampleFromAkkaSources_ThatWasCausingWrongErrorsToBeReportedInTheJavaEditor_t1000752_2() {
    whenOpening("t1000752_2/JavaAPITestActor.java").verifyThat(no).errors.are.reported
  }
  
  @Test
  def wideningAccessModifiersOfInherithedMethod_t1000752_3() {
    whenOpening("t1000752_3/J.java").verifyThat(no).errors.are.reported
  }
}
