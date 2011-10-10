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

object AbstractMethodVerifierTest extends TestProjectSetup("jcompiler")

class AbstractMethodVerifierTest {
  import AbstractMethodVerifierTest._

  private class ProblemReporterAdapter extends IProblemRequestor {
    def acceptProblem(problem: IProblem) {}
    def beginReporting() {}
    def endReporting() {}
    def isActive(): Boolean = true
  }

  @Test
  def t1000594_pos_ErrorsAreDisplayedInJavaEditor_If_JavaClass_IsSubtypeOf_ScalaAbstractClass_ThatInherithedFromATraitWithOnlyConcreteMembers() {
    //when
    val unit = compilationUnit("t1000594_pos/C.java")
    val owner = new WorkingCopyOwner() {
      override def getProblemRequestor(unit: org.eclipse.jdt.core.ICompilationUnit): IProblemRequestor =
        new ProblemReporterAdapter {
          override def acceptProblem(problem: IProblem) {
            //verify
            assert(false, "found problem: " + problem)
          }
        }
    }

    //then
    // this will trigger the java reconciler so that the problems will be reported in the ProblemReporter
    unit.getWorkingCopy(owner, new NullProgressMonitor)
  }

  @Test
  def t1000594_neg_ErrorsAreDisplayedInJavaEditor_If_JavaClass_IsSubtypeOf_ScalaAbstractClass_ThatInherithedFromATraitWithOnlyConcreteMembers() {
    //when
    val unit = compilationUnit("t1000594_neg/C.java")
    
    val requestor = mock(classOf[IProblemRequestor])
    when(requestor.isActive()).thenReturn(true)
    
    val owner = new WorkingCopyOwner() {
      override def getProblemRequestor(unit: org.eclipse.jdt.core.ICompilationUnit): IProblemRequestor = requestor
    }

    // then
    // this will trigger the java reconciler so that the problems will be reported to the `requestor`
    unit.getWorkingCopy(owner, new NullProgressMonitor)

    // verify
    verify(requestor, times(1)).acceptProblem(any())
  }

  @Test
  def JavaClassExtendingScalaClassesWithDeferredMethods_ErrorsAreDisplayedInJavaEditor_t1000607() {
    //when
    val unit = compilationUnit("t1000607/C.java")
    
    val requestor = mock(classOf[IProblemRequestor])
    when(requestor.isActive()).thenReturn(true)
    
    val owner = new WorkingCopyOwner() {
      override def getProblemRequestor(unit: org.eclipse.jdt.core.ICompilationUnit): IProblemRequestor = requestor
    }

    // then
    // this will trigger the java reconciler so that the problems will be reported to the `requestor`
    unit.getWorkingCopy(owner, new NullProgressMonitor)

    // verify
    verify(requestor, times(1)).acceptProblem(any())
  }
}