package org.scalaide.debug.internal

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.After
import org.junit.Assert._
import org.junit.Before
import org.junit.Test
import org.junit.matchers.JUnitMatchers
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.debug.internal.model.ScalaArrayReference
import org.scalaide.debug.internal.model.ScalaDebugModelPresentation
import org.hamcrest.CoreMatchers
import org.scalaide.debug.internal.model.ScalaLogicalStructureProvider

object ScalaDebugComputeDetailTest extends TestProjectSetup("debug", bundleName= "org.scala-ide.sdt.debug.tests") with ScalaDebugRunningTest

/**
 * Test executing computeDetail for object references. This triggers a invocation of 'toString' on
 * the target VM and is simpler to test without mocking.
 */

class ScalaDebugComputeDetailTest {

  import ScalaDebugComputeDetailTest._

  var session: ScalaDebugTestSession = null

  @Before
  def refreshBinaryFiles(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }

  @After
  def cleanDebugSession(): Unit = {
    if (session ne null) {
      session.terminate()
      session = null
    }
  }

  /**
   * test for object reference
   */
  @Test
  def computeDetailObject(): Unit = {
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
  def computeDetailArrayOfMixedElements(): Unit = {
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
  def computeDetailNullReference(): Unit = {
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
  def checkVersionAvailable(): Unit = {
    session = ScalaDebugTestSession(file("HelloWorld.launch"))

    session.runToLine(TYPENAME_HELLOWORLD + "$", 7)

    assertTrue("Unable to find the Scala version", session.debugTarget.is2_9Compatible(session.currentStackFrame.thread))
  }

  /**
   * Check the logical structure returned for a List[Int]
   */
  @Test
  def logicalStructureStringList(): Unit = {
    session = ScalaDebugTestSession(file("Variables.launch"))

    session.runToLine(TYPENAME_VARIABLES + "$", 30)

    session.checkStackFrame(TYPENAME_VARIABLES + "$", "main([Ljava/lang/String;)V", 30)

    val logicalStructure= ScalaLogicalStructureProvider.getLogicalStructure(session.getLocalVariable("j"))

    assertThat("Wrong type for the logical structure", logicalStructure.getValueString(), CoreMatchers.containsString("Array[Object](3)"))

    val elements = logicalStructure.asInstanceOf[ScalaArrayReference].getVariables()
    assertThat("Wrong value for first element", elements(0).getValue().getValueString(), CoreMatchers.containsString("Integer 4"))
    assertThat("Wrong value for second element", elements(1).getValue().getValueString(), CoreMatchers.containsString("Integer 5"))
    assertThat("Wrong value for third element", elements(2).getValue().getValueString(), CoreMatchers.containsString("Integer 6"))
  }

}
