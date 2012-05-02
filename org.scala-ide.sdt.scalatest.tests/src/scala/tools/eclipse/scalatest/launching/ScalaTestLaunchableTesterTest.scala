package scala.tools.eclipse.scalatest.launching

import org.junit.Test
import org.mockito.Mockito._
import org.junit.Assert._
import org.eclipse.jdt.internal.core.PackageFragment
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.javaelements.ScalaClassElement
import org.eclipse.ui.part.FileEditorInput
import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.IAnnotation
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.internal.core.Openable

class ScalaTestLaunchableTesterTest {

  val suiteSubTypeClass = mock(classOf[ScalaClassElement])
  when(suiteSubTypeClass.getSuperInterfaceNames).thenReturn(Array("org.scalatest.Suite"))
  val suiteSubType = suiteSubTypeClass.asInstanceOf[IType]
  
  val wrapWithAnnotation = mock(classOf[IAnnotation])
  when(wrapWithAnnotation.getElementName).thenReturn("WrapWith")
  val annotatedTypeClass = mock(classOf[ScalaClassElement])
  when(annotatedTypeClass.getSuperInterfaceNames).thenReturn(Array.empty[String])
  when(annotatedTypeClass.getAnnotations).thenReturn(Array(wrapWithAnnotation))
  val annotatedType = annotatedTypeClass.asInstanceOf[IType]
  
  val notScalaTestTypeClass = mock(classOf[ScalaClassElement])
  when(notScalaTestTypeClass.getSuperInterfaceNames).thenReturn(Array("org.test.SomethingElse"))
  when(notScalaTestTypeClass.getAnnotations).thenReturn(Array.empty[IAnnotation])
  val notScalaTestType = notScalaTestTypeClass.asInstanceOf[IType]
  
  @Test
  def testScalaTestPackageTester() {
    val tester = new ScalaTestPackageTester()
    val packageFragment = mock(classOf[PackageFragment])
    val scalaSourceFile = mock(classOf[ScalaSourceFile])
    val fileEditorInput = mock(classOf[FileEditorInput])
    val scalaSourceFileEditor = mock(classOf[ScalaSourceFileEditor])
    
    assertTrue(tester.test(packageFragment, "", Array.empty, null))
    assertFalse(tester.test(scalaSourceFile, "", Array.empty, null))
    assertFalse(tester.test(suiteSubTypeClass, "", Array.empty, null))
    assertFalse(tester.test(annotatedTypeClass, "", Array.empty, null))
    assertFalse(tester.test(notScalaTestTypeClass, "", Array.empty, null))
    assertFalse(tester.test(fileEditorInput, "", Array.empty, null))
    assertFalse(tester.test(scalaSourceFileEditor, "", Array.empty, null))
  }
  
  @Test
  def testScalaTestFileTester() {
    val tester = new ScalaTestFileTester()
    val packageFragment = mock(classOf[PackageFragment])
    val sourceFile1 = mock(classOf[ScalaSourceFile])
    when(sourceFile1.getAllTypes).thenReturn(Array(suiteSubType, notScalaTestType))
    val sourceFile2 = mock(classOf[ScalaSourceFile])
    when(sourceFile2.getAllTypes).thenReturn(Array(notScalaTestType))
    val openable1 = mock(classOf[Openable])
    when(openable1.getOpenable).thenReturn(sourceFile1)
    val openable2 = mock(classOf[Openable])
    when(openable2.getOpenable).thenReturn(sourceFile2)
    val fileEditorInput1 = mock(classOf[FileEditorInput])
    when(fileEditorInput1.getAdapter(classOf[IJavaElement])).thenReturn(openable1, openable1)
    val fileEditorInput2 = mock(classOf[FileEditorInput])
    when(fileEditorInput2.getAdapter(classOf[IJavaElement])).thenReturn(openable2, openable2)
    val scalaSourceFileEditor = mock(classOf[ScalaSourceFileEditor])
    
    assertFalse(tester.test(packageFragment, "", Array.empty, null))
    assertTrue(tester.test(sourceFile1, "", Array.empty, null))
    assertFalse(tester.test(sourceFile2, "", Array.empty, null))
    assertFalse(tester.test(suiteSubTypeClass, "", Array.empty, null))
    assertFalse(tester.test(annotatedTypeClass, "", Array.empty, null))
    assertFalse(tester.test(notScalaTestTypeClass, "", Array.empty, null))
    assertTrue(tester.test(fileEditorInput1, "", Array.empty, null))
    assertFalse(tester.test(fileEditorInput2, "", Array.empty, null))
    assertFalse(tester.test(scalaSourceFileEditor, "", Array.empty, null))
  }
  
  @Test
  def testScalaTestSuiteTester() {
    val tester = new ScalaTestSuiteTester()
    val packageFragment = mock(classOf[PackageFragment])
    val scalaSourceFile = mock(classOf[ScalaSourceFile])
    val fileEditorInput = mock(classOf[FileEditorInput])
    val scalaSourceFileEditor = mock(classOf[ScalaSourceFileEditor])
    
    assertFalse(tester.test(packageFragment, "", Array.empty, null))
    assertFalse(tester.test(scalaSourceFile, "", Array.empty, null))
    assertTrue(tester.test(suiteSubTypeClass, "", Array.empty, null))
    assertTrue(tester.test(annotatedTypeClass, "", Array.empty, null))
    assertFalse(tester.test(notScalaTestTypeClass, "", Array.empty, null))
    // TODO: Figure out a way to test the call of PlatformUI.getWorkbench.getActiveWorkbenchWindow.getActivePage.getActiveEditor for FileEditorInput.
    //assertTrue(tester.test(fileEditorInput, "", Array.empty, null))
    assertFalse(tester.test(scalaSourceFileEditor, "", Array.empty, null))
  }
}