package org.scalaide.debug.internal

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.IDebugEventSetListener
import org.eclipse.debug.core.model.IProcess
import org.junit._

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.TestProjectSetup

object ScalaDebuggerDisconnectTests extends TestProjectSetup("debug", bundleName = "org.scala-ide.sdt.debug.tests") with ScalaDebugRunningTest {

  var initialized = false

  def initDebugSession(launchConfigurationName: String): ScalaDebugTestSession = ScalaDebugTestSession(file(launchConfigurationName + ".launch"))

  @AfterClass
  def deleteProject() {
    SDTTestUtils.deleteProjects(project)
  }
}

class ScalaDebuggerDisconnectTests {
  import ScalaDebuggerDisconnectTests._

  final val TIMEOUT = 5000

  private var session: ScalaDebugTestSession = null

  /** Wait for a process to send the TERMINATE debug event. */
  class ProcListener extends IDebugEventSetListener {
    private val processTerminated = new CountDownLatch(1)

    /** Wait until a process TERMINATED event arrives, or timeout expires. */
    def waitForProcessTermination(timeout: Int = TIMEOUT): Unit = try {
      processTerminated.await(timeout, TimeUnit.MILLISECONDS)
    } catch {
      case _: InterruptedException => // do nothing
    }

    override def handleDebugEvents(events: Array[DebugEvent]) {
      for (e <- events) e.getKind() match {
        case DebugEvent.TERMINATE if e.getSource().isInstanceOf[IProcess] =>
          processTerminated.countDown()
        case _ =>
      }
    }
  }

  private var processListener: ProcListener = _

  @Before
  def initializeTests() {
    if (!initialized) {
      project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
      project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
      initialized = true
    }
    // every test gets a new listener
    processListener = new ProcListener
    DebugPlugin.getDefault.addDebugEventListener(processListener)
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

    processListener.waitForProcessTermination()
    Assert.assertTrue("VM not properly terminated", session.debugTarget.virtualMachine.process() eq null)
    Assert.assertTrue("Process not properly terminated", session.debugTarget.getProcess.isTerminated())
  }

  @Test
  def NormalTerminationCleansup() {
    session = initDebugSession("StepFilters")

    session.runToLine(TYPENAME_STEP_FILTERS, 37)
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "bridges()V", 37)
    session.resumeToCompletion()

    processListener.waitForProcessTermination()

    Assert.assertTrue("VM not properly terminated", session.debugTarget.virtualMachine.process() eq null)
    Assert.assertTrue("Process not properly terminated", session.debugTarget.getProcess.isTerminated())
  }
}