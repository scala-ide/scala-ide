package scala.tools.eclipse.scalatest.launching

import org.junit.Test
import org.junit.Assert._
import scala.tools.eclipse.javaelements.ScalaSourceFile

class ScalaTestLaunchShortcutTest {
  
  import ScalaTestProject._
  
  @Test
  def testGetScalaTestSuites() {
    val singleSpecSuiteList = ScalaTestLaunchShortcut.getScalaTestSuites(file("src/com/test/SingleSpec.scala"))
    assertEquals(1, singleSpecSuiteList.size)
    assertTrue(singleSpecSuiteList.exists(t => t.getFullyQualifiedName == "com.test.SingleSpec"))
    
    val multiSpecSuiteList = ScalaTestLaunchShortcut.getScalaTestSuites(file("src/com/test/MultiSpec.scala"))
    assertEquals(3, multiSpecSuiteList.size)
    assertTrue(multiSpecSuiteList.exists(t => t.getFullyQualifiedName == "com.test.TestingFunSuite"))
    assertTrue(multiSpecSuiteList.exists(t => t.getFullyQualifiedName == "com.test.TestingFreeSpec"))
    assertTrue(multiSpecSuiteList.exists(t => t.getFullyQualifiedName == "com.test.StackSpec2"))
    
    /*val exampleSpec1SuiteList = ScalaTestLaunchShortcut.getScalaTestSuites(file("src/com/test/ExampleSpec1.scala"))
    assertEquals(1, exampleSpec1SuiteList.size)
    assertTrue(exampleSpec1SuiteList.exists(t => t.getFullyQualifiedName == "com.test.ExampleSpec1"))
    
    val stringSpecificationSuiteList = ScalaTestLaunchShortcut.getScalaTestSuites(file("src/com/test/StringSpecification.scala"))
    assertEquals(1, stringSpecificationSuiteList.size)
    assertTrue(stringSpecificationSuiteList.exists(t => t.getFullyQualifiedName == "com.test.StringSpecification"))*/
  }
  
  @Test
  def testIsScalaTestSuite() {
    val singleSpecFile = scalaCompilationUnit("com/test/SingleSpec.scala")
    singleSpecFile.getAllTypes.foreach { t =>
      assertTrue(t.getFullyQualifiedName + " expected as a ScalaTest suite, but it is not.", ScalaTestLaunchShortcut.isScalaTestSuite(t))
    }
    
    val multiSpecFile = scalaCompilationUnit("com/test/MultiSpec.scala")
    multiSpecFile.getAllTypes.foreach { t =>
      if (t.getFullyQualifiedName == "com.test.Fraction")
        assertFalse(t.getFullyQualifiedName + " expected as not a ScalaTest suite, but it is.", ScalaTestLaunchShortcut.isScalaTestSuite(t))
      else
        assertTrue(t.getFullyQualifiedName + " expected as a ScalaTest suite, but it is not.", ScalaTestLaunchShortcut.isScalaTestSuite(t))
    }
    
    /*val exampleSpec1File = scalaCompilationUnit("com/test/ExampleSpec1.scala")
    exampleSpec1File.getAllTypes.foreach { t =>
      assertTrue(t.getFullyQualifiedName + " expected as a ScalaTest suite, but it is not.", ScalaTestLaunchShortcut.isScalaTestSuite(t))
    }
    
    val stringSpecificationFile = scalaCompilationUnit("com/test/StringSpecification.scala")
    stringSpecificationFile.getAllTypes.foreach { t =>
      assertTrue(t.getFullyQualifiedName + " expected as a ScalaTest suite, but it is not.", ScalaTestLaunchShortcut.isScalaTestSuite(t))
    }*/
  }
  
  @Test
  def testContainsScalaTestSuite() {
    val singleSpecFile = scalaCompilationUnit("com/test/SingleSpec.scala").asInstanceOf[ScalaSourceFile]
    assertTrue(ScalaTestLaunchShortcut.containsScalaTestSuite(singleSpecFile))
    
    val multiSpecFile = scalaCompilationUnit("com/test/MultiSpec.scala").asInstanceOf[ScalaSourceFile]
    assertTrue(ScalaTestLaunchShortcut.containsScalaTestSuite(multiSpecFile))
    
    /*val exampleSpec1File = scalaCompilationUnit("com/test/ExampleSpec1.scala").asInstanceOf[ScalaSourceFile]
    assertTrue(ScalaTestLaunchShortcut.containsScalaTestSuite(exampleSpec1File))
    
    val stringSpecificationFile = scalaCompilationUnit("com/test/StringSpecification.scala").asInstanceOf[ScalaSourceFile]
    assertTrue(ScalaTestLaunchShortcut.containsScalaTestSuite(stringSpecificationFile))*/
  }
  
  @Test
  def testGetClassElement() {
    val singleSpecFile = scalaCompilationUnit("com/test/SingleSpec.scala").asInstanceOf[ScalaSourceFile]
    val allTypes = singleSpecFile.getAllTypes
    assertEquals(1, allTypes.size)
    val singleSpecType = allTypes(0)
    val children = singleSpecType.getChildren
    children.foreach { c =>
      assertEquals("com.test.SingleSpec", ScalaTestLaunchShortcut.getClassElement(c).getFullyQualifiedName)
    }
  }
}