package scala.tools.eclipse.debug

import org.eclipse.core.resources.IncrementalProjectBuilder
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.junit.After
import org.junit.Before
import org.junit.AfterClass
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Test
import scala.tools.eclipse.util.EclipseUtils
import java.util.concurrent.CountDownLatch
import org.eclipse.debug.core.IBreakpointListener
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.core.resources.IMarkerDelta
import java.util.concurrent.TimeUnit
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.core.resources.IMarker

object ScalaDebugBreakpointTest extends TestProjectSetup("breakpoints", bundleName = "org.scala-ide.sdt.debug.tests") with ScalaDebugRunningTest {
  val BP_TYPENAME = "breakpoints.Breakpoints"

  var initialized = false

  def initDebugSession(launchConfigurationName: String): ScalaDebugTestSession = new ScalaDebugTestSession(file(launchConfigurationName + ".launch"))

  @AfterClass
  def deleteProject() {
    SDTTestUtils.deleteProjects(project)
  }
}

class ScalaDebugBreakpointTest {

  import ScalaDebugBreakpointTest._

  var session: ScalaDebugTestSession = null

  @Before
  def initializeTests() {
    SDTTestUtils.enableAutoBuild(false)
    if (!initialized) {
      project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
      project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
      initialized = true
    }
  }

  @After
  def cleanDebugSession() {
    if (session ne null) {
      session.terminate()
      session = null
    }
  }

  @Test
  def simpleBreakpointEnableDisable() {
    session = initDebugSession("Breakpoints")
    session.runToLine("breakpoints.Breakpoints", 32) // stop in main

    val bp11 = session.addLineBreakpoint(BP_TYPENAME, 11)
    val bp13 = session.addLineBreakpoint(BP_TYPENAME, 13)
    try {
      session.waitForBreakpointsToBeEnabled(bp11, bp13)

      session.resumetoSuspension()
      session.checkStackFrame(BP_TYPENAME, "simple1()V", 11)

      session.resumetoSuspension()
      session.checkStackFrame(BP_TYPENAME, "simple1()V", 13)

      bp11.setEnabled(false)
      session.waitForBreakpointsToBeDisabled(bp11)

      session.resumetoSuspension()
      session.checkStackFrame(BP_TYPENAME, "simple1()V", 13)

      bp11.setEnabled(true); bp13.setEnabled(false)

      session.waitForBreakpointsToBeEnabled(bp11)
      session.waitForBreakpointsToBeDisabled(bp13)

      session.resumetoSuspension()
      session.checkStackFrame(BP_TYPENAME, "simple1()V", 11)

      session.resumetoSuspension()
      session.checkStackFrame(BP_TYPENAME, "simple1()V", 11)

      session.resumeToCompletion()
    } finally {
      bp11.delete()
      bp13.delete()
    }
  }

  /** Test that disabling a breakpoint in a closure does not disable
   *  the second one, in a different closure (ClassPrepareEvents might be the same)
   */
  @Test
  def breakpointsWithClosures() {
    session = initDebugSession("Breakpoints")
    session.runToLine("breakpoints.Breakpoints", 32) // stop in main

    val bp20 = session.addLineBreakpoint(BP_TYPENAME, 20)
    val bp22 = session.addLineBreakpoint(BP_TYPENAME, 22)
    val bp26 = session.addLineBreakpoint(BP_TYPENAME, 26)
    try {
      session.waitForBreakpointsToBeEnabled(bp20, bp22, bp26)

      session.resumetoSuspension()
      session.checkStackFrame(BP_TYPENAME, "fors()V", 20)

      bp22.setEnabled(false)
      session.waitForBreakpointsToBeDisabled(bp22)

      session.resumetoSuspension()
      session.checkStackFrame(BP_TYPENAME + "$$anonfun$fors$2", "apply$mcVI$sp(I)V", 26)

      bp26.setEnabled(false)
      session.waitForBreakpointsToBeDisabled(bp26)

      session.resumeToCompletion()
    } finally {
      bp20.delete(); bp22.delete(); bp26.delete()
    }
  }

  /** Test that a breakpoint that starts in the disabled state can still be enabled
   *  and function properly.
   */
  @Test
  def breakpointsStartDisabled() {
    session = initDebugSession("Breakpoints")
    val bp10 = session.addLineBreakpoint(BP_TYPENAME, 10)
    val bp11 = session.addLineBreakpoint(BP_TYPENAME, 11)
    val bp16 = session.addLineBreakpoint(BP_TYPENAME, 16)

    val allBps = Seq(bp10, bp11, bp16)
    allBps.foreach(_.setEnabled(false))
    session.waitForBreakpointsToBeDisabled(allBps: _*)

    session.runToLine("breakpoints.Breakpoints", 32) // stop in main

    try {

      bp11.setEnabled(true)
      session.waitForBreakpointsToBeEnabled(bp11)

      session.resumetoSuspension()
      session.checkStackFrame(BP_TYPENAME, "simple1()V", 11)

      bp11.setEnabled(false)
      session.waitForBreakpointsToBeDisabled(bp11)

      session.resumeToCompletion()
    } finally {
      bp10.delete(); bp11.delete(); bp16.delete()
    }
  }

  /** Test disabling and re-enabling a breakpoint does work (class prepare events are still handled)  */
  @Test
  def breakpointsDisbaleDoesNotInterfereWithLoadedClasses() {
    session = initDebugSession("Breakpoints")
    val bp22 = session.addLineBreakpoint(BP_TYPENAME, 22)

    bp22.setEnabled(true)
    session.waitForBreakpointsToBeEnabled(bp22)

    session.runToLine("breakpoints.Breakpoints", 32) // stop in main

    try {

      bp22.setEnabled(false)
      session.waitForBreakpointsToBeDisabled(bp22)

      bp22.setEnabled(true)
      session.waitForBreakpointsToBeEnabled(bp22)

      session.resumetoSuspension()
      session.checkStackFrame(BP_TYPENAME + "$$anonfun$fors$1", "apply$mcVI$sp(I)V", 22)

      bp22.setEnabled(false)
      session.waitForBreakpointsToBeDisabled(bp22)

      session.resumeToCompletion()
    } finally {
      bp22.delete()
    }
  }

  /** Test disabling and re-enabling a breakpoint does work (class prepare events are still handled)  */
  @Test
  def breakpointsDisbaleDoesNotInterfereWithLoadedClassesWhenTwoBreakpointsWatchTheSameClasses() {
    session = initDebugSession("Breakpoints")
    // both breakpoints watch the same class prepare events (are in the same method, but in different closures)
    val bp21 = session.addLineBreakpoint(BP_TYPENAME, 21)
    val bp22 = session.addLineBreakpoint(BP_TYPENAME, 22)

    val allBps = Seq(bp21, bp22)
    allBps.foreach(_.setEnabled(false))
    session.waitForBreakpointsToBeDisabled(allBps: _*)

    session.runToLine("breakpoints.Breakpoints", 32) // stop in main

    try {
      bp21.setEnabled(true)
      session.waitForBreakpointsToBeEnabled(bp21)

      session.resumetoSuspension()
      session.checkStackFrame(BP_TYPENAME + "$$anonfun$fors$1", "apply$mcVI$sp(I)V", 21)
      // by now, the class has been loaded, so ClassPrepareEvent won't be triggered for the next breakpoint

      bp21.setEnabled(false)
      session.waitForBreakpointsToBeDisabled(bp21)

      // this enables the breakpoint, but if it missed ClassPrepareEvent, and the
      // breakpoint was not yet installed, this breakpoint won't be hit
      bp22.setEnabled(true)
      session.waitForBreakpointsToBeEnabled(bp22)

      session.resumetoSuspension()
      session.checkStackFrame(BP_TYPENAME + "$$anonfun$fors$1", "apply$mcVI$sp(I)V", 22)

      bp22.setEnabled(false)
      session.waitForBreakpointsToBeDisabled(bp22)

      session.resumeToCompletion()
    } finally {
      bp21.delete(); bp22.delete()
    }
  }
}