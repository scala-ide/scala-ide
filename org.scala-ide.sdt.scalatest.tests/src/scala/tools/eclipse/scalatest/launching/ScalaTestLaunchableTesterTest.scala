package scala.tools.eclipse.scalatest.launching

import org.junit.Test
import org.junit.Assert._

class ScalaTestLaunchableTesterTest {
  
  import ScalaTestProject._
  
  @Test
  def testScalaTestPackageTester() {
    val tester = new ScalaTestPackageTester()
    
    val comTestPackage = getPackageFragment("com.test")
    assertTrue(tester.test(comTestPackage, "", Array.empty, null))
    
    val singleSpecFile = scalaCompilationUnit("com/test/SingleSpec.scala")
    assertFalse(tester.test(singleSpecFile, "", Array.empty, null))
    
    singleSpecFile.getAllTypes.foreach { t => 
      assertFalse(tester.test(t, "", Array.empty, null))      
    }
  }
  
  @Test
  def testScalaTestFileTester() {
    val tester = new ScalaTestFileTester()
    
    val comTestPackage = getPackageFragment("com.test")
    assertFalse(tester.test(comTestPackage, "", Array.empty, null))
    
    val singleSpecFile = scalaCompilationUnit("com/test/SingleSpec.scala")
    assertTrue(tester.test(singleSpecFile, "", Array.empty, null))
    
    singleSpecFile.getAllTypes.foreach { t => 
      assertFalse(tester.test(t, "", Array.empty, null))      
    }
  }
  
  @Test
  def testScalaTestSuiteTester() {
    val tester = new ScalaTestSuiteTester()
    
    val comTestPackage = getPackageFragment("com.test")
    assertFalse(tester.test(comTestPackage, "", Array.empty, null))
    
    val multiSpecFile = scalaCompilationUnit("com/test/MultiSpec.scala")
    assertFalse(tester.test(multiSpecFile, "", Array.empty, null))
    
    multiSpecFile.getAllTypes.foreach { t => 
      if (t.getFullyQualifiedName == "com.test.Fraction")
        assertFalse(tester.test(t, "", Array.empty, null))
      else
        assertTrue(tester.test(t, "", Array.empty, null))
    }
  }
}