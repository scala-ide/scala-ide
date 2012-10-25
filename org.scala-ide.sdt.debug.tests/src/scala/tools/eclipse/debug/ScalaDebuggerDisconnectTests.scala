package scala.tools.eclipse.debug

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.preferences.InstanceScope
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.IDebugEventSetListener
import org.eclipse.debug.core.model.IProcess
import org.eclipse.ui.preferences.ScopedPreferenceStore
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.testsetup.TestProjectSetup

object ScalaDebuggerDisconnectTests extends TestProjectSetup("debug", bundleName = "org.scala-ide.sdt.debug.tests") with ScalaDebugRunningTest {

  var initialized = false

  def initDebugSession(launchConfigurationName: String): ScalaDebugTestSession = new ScalaDebugTestSession(file(launchConfigurationName + ".launch"))

  @AfterClass
  def deleteProject() {
    SDTTestUtils.deleteProjects(project)
  }
}

class ScalaDebuggerDisconnectTests {
  import ScalaDebuggerDisconnectTests._

  final val TIMEOUT = 5000

  private var session: ScalaDebugTestSession = null

  object ProcListener extends IDebugEventSetListener {
    @volatile
    private var processTerminated = false

    def reset() { processTerminated = false }

    /** Wait until a process TERMINATED event arrives, or timeout expires. */
    def waitForProcessTermination(timeout: Int) {
      var waited = 0
      while (waited < timeout && !processTerminated) {
        Thread.sleep(100)
        waited += 100
      }
    }

    override def handleDebugEvents(events: Array[DebugEvent]) {
      for (e <- events) e.getKind() match {
        case DebugEvent.TERMINATE if e.getSource().isInstanceOf[IProcess] =>
          processTerminated = true
        case _ =>
      }
    }
  }

  @Before
  def initializeTests() {
    if (!initialized) {
      project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
      project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
      initialized = true
    }
    DebugPlugin.getDefault.addDebugEventListener(ProcListener)
    ProcListener.reset()
  }

  @After
  def cleanDebugSession() {
    if (session ne null) {
      session.terminate()
      session = null
    }
  }

  @Test
  def BreakpointHitAndTerminateWorks() {
    session = initDebugSession("StepFilters")

    session.runToLine(TYPENAME_STEP_FILTERS, 37)
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "bridges()V", 37)

    session.terminate()

    ProcListener.waitForProcessTermination(TIMEOUT)
    Assert.assertTrue("VM not properly terminated", session.debugTarget.virtualMachine.process() eq null)
    Assert.assertTrue("Process not properly terminated", session.debugTarget.getProcess.isTerminated())
  }

  @Test
  def NormalTerminationCleansup() {
    session = initDebugSession("StepFilters")

    session.runToLine(TYPENAME_STEP_FILTERS, 37)
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "bridges()V", 37)
    session.resumeToCompletion()

    ProcListener.waitForProcessTermination(TIMEOUT)

    Assert.assertTrue("VM not properly terminated", session.debugTarget.virtualMachine.process() eq null)
    Assert.assertTrue("Process not properly terminated", session.debugTarget.getProcess.isTerminated())
  }
}