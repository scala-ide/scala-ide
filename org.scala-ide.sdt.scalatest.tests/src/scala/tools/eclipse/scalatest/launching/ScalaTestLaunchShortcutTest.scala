package scala.tools.eclipse.scalatest.launching

import org.junit.Test
import org.mockito.Mockito._
import org.junit.Assert._
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.IAnnotation
import scala.tools.eclipse.javaelements.ScalaClassElement
import org.eclipse.jdt.internal.core.Openable
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.jdt.core.IJavaElement
import scala.tools.eclipse.javaelements.ScalaElement

class ScalaTestLaunchShortcutTest {

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
  def testGetScalaTestSuites() {
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
    
    val openable1Result = ScalaTestLaunchShortcut.getScalaTestSuites(fileEditorInput1)
    assertEquals(1, openable1Result.size)
    assertEquals(suiteSubType, openable1Result(0))
    
    val openable2Result = ScalaTestLaunchShortcut.getScalaTestSuites(fileEditorInput2)
    assertEquals(0, openable2Result.size)
  }
  
  @Test
  def testIsScalaTestSuite() {
    assertTrue(ScalaTestLaunchShortcut.isScalaTestSuite(suiteSubType))
    assertTrue(ScalaTestLaunchShortcut.isScalaTestSuite(annotatedType))
    assertFalse(ScalaTestLaunchShortcut.isScalaTestSuite(notScalaTestType))
  }
  
  @Test
  def testContainsScalaTestSuite() {
    val sourceFile1 = mock(classOf[ScalaSourceFile])
    when(sourceFile1.getAllTypes).thenReturn(Array(suiteSubType, annotatedType, notScalaTestType))
    assertTrue(ScalaTestLaunchShortcut.containsScalaTestSuite(sourceFile1))
    
    val sourceFile2 = mock(classOf[ScalaSourceFile])
    when(sourceFile2.getAllTypes).thenReturn(Array(annotatedType, notScalaTestType))
    assertTrue(ScalaTestLaunchShortcut.containsScalaTestSuite(sourceFile2))
    
    val sourceFile3 = mock(classOf[ScalaSourceFile])
    when(sourceFile3.getAllTypes).thenReturn(Array(suiteSubType, notScalaTestType))
    assertTrue(ScalaTestLaunchShortcut.containsScalaTestSuite(sourceFile3))
    
    val sourceFile4 = mock(classOf[ScalaSourceFile])
    when(sourceFile4.getAllTypes).thenReturn(Array(notScalaTestType))
    assertFalse(ScalaTestLaunchShortcut.containsScalaTestSuite(sourceFile4))
    
    val sourceFile5 = mock(classOf[ScalaSourceFile])
    when(sourceFile5.getAllTypes).thenReturn(Array(suiteSubType))
    assertTrue(ScalaTestLaunchShortcut.containsScalaTestSuite(sourceFile5))
    
    val sourceFile6 = mock(classOf[ScalaSourceFile])
    when(sourceFile6.getAllTypes).thenReturn(Array(annotatedType))
    assertTrue(ScalaTestLaunchShortcut.containsScalaTestSuite(sourceFile6))
  }
  
  @Test
  def testGetClassElement() {
    val javaElement = mock(classOf[IJavaElement])
    val scalaClassElement = mock(classOf[ScalaClassElement])
    when(javaElement.getParent).thenReturn(scalaClassElement)
    assertEquals(scalaClassElement, ScalaTestLaunchShortcut.getClassElement(javaElement))
  }
}