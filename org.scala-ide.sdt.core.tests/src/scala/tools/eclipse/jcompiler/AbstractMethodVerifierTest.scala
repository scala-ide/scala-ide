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

object AbstractMethodVerifierTest extends TestProjectSetup("jcompiler")

@Ignore("Enable this test class when ticket http://scala-ide-portfolio.assembla.com/spaces/scala-ide/tickets/1000662 is fixed.")
class AbstractMethodVerifierTest {
  import AbstractMethodVerifierTest._

  @Test
  def javaClassExtendingScalaClassWithConcreteMethodsInSuperTrait_NoErrorIsDisplayedInJavaEditor_t1000594_pos() {
    //when
    val unit = compilationUnit("t1000594_pos/C.java")
    
    val requestor = mock(classOf[IProblemRequestor])
    when(requestor.isActive()).thenReturn(true)
    
    val owner = mock(classOf[WorkingCopyOwner])
    when(owner.getProblemRequestor(any())).thenReturn(requestor)
    
    //then
    // this will trigger the java reconciler so that the problems will be reported in the ProblemReporter
    unit.getWorkingCopy(owner, new NullProgressMonitor)
    
    // verify
    verify(requestor, never()).acceptProblem(any())
  }

  @Test
  def javaClassExtendingScalaClassWithDeferredMethodsInSuperTrait_ErrorsAreDisplayedInJavaEditor_t1000594_neg() {
    //when
    val unit = compilationUnit("t1000594_neg/C.java")
    
    val requestor = mock(classOf[IProblemRequestor])
    when(requestor.isActive()).thenReturn(true)
    
    val owner = mock(classOf[WorkingCopyOwner])
    when(owner.getProblemRequestor(any())).thenReturn(requestor)

    // then
    // this will trigger the java reconciler so that the problems will be reported to the `requestor`
    unit.getWorkingCopy(owner, new NullProgressMonitor)

    // verify
    verify(requestor, times(1)).acceptProblem(any())
  }

  @Test
  def javaClassExtendingScalaClassWithDeferredMethodsInSuperTrait_ErrorsAreDisplayedInJavaEditor_t1000607() {
    //when
    val unit = compilationUnit("t1000607/C.java")
    
    val requestor = mock(classOf[IProblemRequestor])
    when(requestor.isActive()).thenReturn(true)
    
    val owner = mock(classOf[WorkingCopyOwner])
    when(owner.getProblemRequestor(any())).thenReturn(requestor)
    
    // then
    // this will trigger the java reconciler so that the problems will be reported to the `requestor`
    unit.getWorkingCopy(owner, new NullProgressMonitor)

    // verify
    verify(requestor, times(1)).acceptProblem(any())
  }
}