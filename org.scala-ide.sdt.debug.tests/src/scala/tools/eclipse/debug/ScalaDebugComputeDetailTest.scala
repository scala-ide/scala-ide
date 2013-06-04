package scala.tools.eclipse.debug

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Before
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.After
import org.junit.Test
import org.junit.Assert._
import scala.tools.eclipse.debug.model.ScalaDebugModelPresentation
import scala.tools.eclipse.debug.model.ScalaCollectionLogicalStructureType
import scala.tools.eclipse.debug.model.ScalaArrayReference
import org.junit.internal.matchers.StringContains
import scala.tools.eclipse.debug.model.ScalaPrimitiveValue

object ScalaDebugComputeDetailTest extends TestProjectSetup("debug", bundleName= "org.scala-ide.sdt.debug.tests") with ScalaDebugRunningTest

/**
 * Test executing computeDetail for object references. This triggers a invocation of 'toString' on 
 * the target VM and is simpler to test without mocking.
 */

class ScalaDebugComputeDetailTest {
  
  import ScalaDebugComputeDetailTest._
  
  var session: ScalaDebugTestSession = null
  
  @Before
  def refreshBinaryFiles() {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }

  @After
  def cleanDebugSession() {
    if (session ne null) {
      session.terminate()
      session = null
    }
  }
  
  /**
   * test for object reference
   */
  @Test
  def computeDetailObject() {
    session = ScalaDebugTestSession(file("Variables.launch"))

    session.runToLine(TYPENAME_VARIABLES + "$", 30)
    
    session.checkStackFrame(TYPENAME_VARIABLES + "$", "main([Ljava/lang/String;)V", 30)
    
    val detail= ScalaDebugModelPresentation.computeDetail(session.getLocalVariable("j"))
    
    assertEquals("Bad detail for object", "List(4, 5, 6)", detail)
  }
  
  /**
   * test for array reference containing object references
   */
  @Test
  def computeDetailArrayOfMixedElements() {
    session = ScalaDebugTestSession(file("Variables.launch"))

    session.runToLine(TYPENAME_VARIABLES + "$", 30)
    
    session.checkStackFrame(TYPENAME_VARIABLES + "$", "main([Ljava/lang/String;)V", 30)
    
    val detail= ScalaDebugModelPresentation.computeDetail(session.getLocalVariable("k"))
    
    assertEquals("Bad detail for mixed elements array", "Array(one, 1, true)", detail)
  }
  
  /**
   * test for a <code>null</code> value.
   */
  @Test
  def computeDetailNullReference() {
    session = ScalaDebugTestSession(file("Variables.launch"))

    session.runToLine(TYPENAME_VARIABLES + "$", 30)
    
    session.checkStackFrame(TYPENAME_VARIABLES + "$", "main([Ljava/lang/String;)V", 30)
    
    val detail= ScalaDebugModelPresentation.computeDetail(session.getLocalVariable("l"))
    
    assertEquals("Bad detail for mixed elements array", "null", detail)
  }
  
  /**
   * Check that we can read the version of Scala running on the debugged VM.
   */
  @Test
  def checkVersionAvailable() {
    session = ScalaDebugTestSession(file("HelloWorld.launch"))

    session.runToLine(TYPENAME_HELLOWORLD + "$", 7)
    
    assertTrue("Unable to find the Scala version", session.debugTarget.is2_9Compatible(session.currentStackFrame.thread))
  }
  
  /**
   * Check the logical structure returned for a List[Int]
   */
  @Test
  def logicalStructureStringList() {
    session = ScalaDebugTestSession(file("Variables.launch"))

    session.runToLine(TYPENAME_VARIABLES + "$", 30)
    
    session.checkStackFrame(TYPENAME_VARIABLES + "$", "main([Ljava/lang/String;)V", 30)
    
    val logicalStructure= ScalaCollectionLogicalStructureType.getLogicalStructure(session.getLocalVariable("j"))

    assertThat("Wrong type for the logical structure", logicalStructure.getValueString(), StringContains.containsString("Array[Object](3)"))
    
    val elements = logicalStructure.asInstanceOf[ScalaArrayReference].getVariables()
    assertThat("Wrong value for first element", elements(0).getValue().getValueString(), StringContains.containsString("Integer 4"))
    assertThat("Wrong value for second element", elements(1).getValue().getValueString(), StringContains.containsString("Integer 5"))
    assertThat("Wrong value for third element", elements(2).getValue().getValueString(), StringContains.containsString("Integer 6"))
  }
  
}