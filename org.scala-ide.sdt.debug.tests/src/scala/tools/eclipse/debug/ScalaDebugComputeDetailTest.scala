package scala.tools.eclipse.debug

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Before
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.After
import org.junit.Test
import org.junit.Assert._
import scala.tools.eclipse.debug.model.ScalaDebugModelPresentation

object ScalaDebugComputeDetailTest extends TestProjectSetup("debug", bundleName= "org.scala-ide.sdt.debug.tests") with ScalaDebugRunningTest

/**
 * Test executing computeDetail for object references. This triggers a invocation of 'toString' on 
 * the target VM and is simpler to test without mocking.
 */

class ScalaDebugComputeDetailTest {
  
  import ScalaDebugComputeDetailTest._
  
  var session: ScalaDebugTestSession = null
  
  @Before
  def setScalaDebugMode() {
    ScalaDebugPlugin.plugin.getPreferenceStore.setValue(DebugPreferencePage.P_ENABLE, true)
  }
  
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
    session = new ScalaDebugTestSession(file("Variables.launch"))

    session.runToLine(TYPENAME_VARIABLES + "$", 30)
    
    session.checkStackFrame(TYPENAME_VARIABLES + "$", "main([Ljava/lang/String;)V", 30)
    
    val detail= ScalaDebugModelPresentation.computeDetail(session.currentStackFrame.variables.find(_.getName == "j").get.getValue)
    
    assertEquals("Bad detail for object", "List(4, 5, 6)", detail)
  }
  
  /**
   * test for array reference containing object references
   */
  @Test
  def computeDetailArrayOfMixedElements() {
    session = new ScalaDebugTestSession(file("Variables.launch"))

    session.runToLine(TYPENAME_VARIABLES + "$", 30)
    
    session.checkStackFrame(TYPENAME_VARIABLES + "$", "main([Ljava/lang/String;)V", 30)
    
    val detail= ScalaDebugModelPresentation.computeDetail(session.currentStackFrame.variables.find(_.getName == "k").get.getValue)
    
    assertEquals("Bad detail for mixed elements array", "Array(one, 1, true)", detail)
  }

}