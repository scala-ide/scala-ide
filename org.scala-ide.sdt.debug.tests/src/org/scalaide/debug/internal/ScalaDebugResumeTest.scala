package org.scalaide.debug.internal

import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.core.testsetup.SDTTestUtils
import org.junit.Test
import org.junit.Before
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.junit.After
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.AfterClass

object ScalaDebugResumeTest extends TestProjectSetup("debug", bundleName = "org.scala-ide.sdt.debug.tests") with ScalaDebugRunningTest {
  @AfterClass
  def deleteProject() {
    SDTTestUtils.deleteProjects(project)
  }
}

/** Test the resume action
 */
class ScalaDebugResumeTest {

  import ScalaDebugResumeTest._

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

  @Test
  def resumeToBreakpoindAndToCompletion() {

    session = ScalaDebugTestSession(file("ForComprehensionListString.launch"))

    session.runToLine(TYPENAME_FC_LS + "$", 9)

    session.checkStackFrame(TYPENAME_FC_LS + "$", "main([Ljava/lang/String;)V", 9)

    session.runToLine(TYPENAME_FC_LS, 35)

    session.checkStackFrame(TYPENAME_FC_LS, "bar()V", 35)

    session.resumeToCompletion()
  }

}