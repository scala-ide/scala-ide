package scala.tools.eclipse.debug

import scala.tools.eclipse.debug.model.{ ScalaThread, ScalaStackFrame, ScalaDebugTarget }
import org.eclipse.core.resources.{ ResourcesPlugin, IFile }
import org.eclipse.debug.core.{ ILaunchManager, IDebugEventSetListener, DebugPlugin, DebugEvent }
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.jdt.debug.core.JDIDebugModel
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget
import org.hamcrest.CoreMatchers._
import org.junit.Assert._
import org.eclipse.debug.core.model.DebugElement
import scala.tools.eclipse.debug.model.ScalaDebugElement
import org.eclipse.jdt.internal.debug.core.model.JDIDebugElement
import org.eclipse.debug.core.IBreakpointListener
import java.util.concurrent.CountDownLatch
import org.eclipse.core.resources.IMarkerDelta
import java.util.concurrent.TimeUnit
import org.eclipse.core.resources.IMarker
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.debug.breakpoints.BreakpointSupport

object EclipseDebugEvent {
  def unapply(event: DebugEvent): Option[(Int, DebugElement)] = {
    event.getSource match {
      case debugElement: DebugElement =>
        Some(event.getKind, debugElement)
      case _ =>
        None
    }
  }
}

class ScalaDebugTestSession(launchConfigurationFile: IFile) extends IDebugEventSetListener with HasLogger {

  object State extends Enumeration {
    type State = Value
    val ACTION_REQUESTED, NOT_LAUNCHED, RUNNING, SUSPENDED, TERMINATED = Value
  }
  import State._

  // from IDebugEventSetListener

  DebugPlugin.getDefault.addDebugEventListener(this)
  val breakpointManager = DebugPlugin.getDefault().getBreakpointManager()


  def handleDebugEvents(events: Array[DebugEvent]) {

    events.foreach(
      _ match {
        case EclipseDebugEvent(DebugEvent.CREATE, target: ScalaDebugTarget) =>
          setLaunched(target)
        case EclipseDebugEvent(DebugEvent.RESUME, x) =>
          setRunning
        case EclipseDebugEvent(DebugEvent.SUSPEND, thread: ScalaThread) =>
          setSuspended(thread.getTopStackFrame.asInstanceOf[ScalaStackFrame])
        case EclipseDebugEvent(DebugEvent.SUSPEND, target: ScalaDebugTarget) if target == debugTarget =>
          setSuspended(null)
        case EclipseDebugEvent(DebugEvent.TERMINATE, target: ScalaDebugTarget) if target == debugTarget =>
          setTerminated()
        case _ =>
      }
    )

  }

  // ----

  def setLaunched(target: ScalaDebugTarget) {
    this.synchronized {
      debugTarget = target
      setRunning
    }
  }

  def setActionRequested() {
    state = ACTION_REQUESTED
  }

  def setRunning() {
    this.synchronized {
      state = RUNNING
      currentStackFrame = null
    }
  }

  def setSuspended(stackFrame: ScalaStackFrame) {
    this.synchronized {
      currentStackFrame = stackFrame
      ScalaDebugger.currentThread = stackFrame.thread
      state = SUSPENDED
      logger.info("SUSPENDED at: %s:%d".format(stackFrame.getMethodFullName, stackFrame.getLineNumber))
      this.notify
    }
  }

  def setTerminated() {
    this.synchronized {
      state = TERMINATED
      this.notify
    }
  }

  def waitUntilSuspended() {
    this.synchronized {
      if (state != SUSPENDED && state != TERMINATED)
        this.wait
    }
  }

  def waitUntilTerminated() {
    this.synchronized {
      if (state != TERMINATED)
        this.wait
    }
  }

  // ----

  var state = NOT_LAUNCHED
  var debugTarget: ScalaDebugTarget = null
  var currentStackFrame: ScalaStackFrame = null
  
  def runToLine(typeName: String, breakpointLine: Int) {
    assertThat("Bad state before runToBreakpoint", state, anyOf(is(NOT_LAUNCHED), is(SUSPENDED)))

    val breakpoint = addLineBreakpoint(typeName, breakpointLine)

    if (state eq NOT_LAUNCHED) {
      launch()
    } else {
      setActionRequested
      currentStackFrame.resume
    }

    waitUntilSuspended
    removeBreakpoint(breakpoint)

    assertEquals("Bad state after runToBreakpoint", SUSPENDED, state)
  }
  
  /**
   * Add a breakpoint in the given type and its nested types at the given line (1 based)
   */
  def addLineBreakpoint(typeName: String, breakpointLine: Int): IJavaLineBreakpoint = {
    val breakpoint = JDIDebugModel.createLineBreakpoint(ResourcesPlugin.getWorkspace.getRoot, typeName, breakpointLine, /*char start*/ -1, /*char end*/ -1, /*hit count*/ -1, /*register*/ true , /*attributes*/ null)
    waitForBreakpointsToBeEnabled(breakpoint)
    breakpoint
  }
  
  /**
   * Remove the given breakpoint
   */
  def removeBreakpoint(breakpoint: IBreakpoint) {
    breakpoint.delete()
  }

  def stepOver() {
    assertEquals("Bad state before stepOver", SUSPENDED, state)

    setActionRequested
    currentStackFrame.stepOver

    waitUntilSuspended

    assertEquals("Bad state after stepOver", SUSPENDED, state)
  }

  def stepInto() {
    assertEquals("Bad state before stepIn", SUSPENDED, state)

    setActionRequested
    currentStackFrame.stepInto

    waitUntilSuspended

    assertEquals("Bad state after stepIn", SUSPENDED, state)
  }

  def stepReturn() {
    assertEquals("Bad state before stepReturn", SUSPENDED, state)

    setActionRequested
    currentStackFrame.stepReturn

    waitUntilSuspended

    assertEquals("Bad state after stepReturn", SUSPENDED, state)
  }

  def resumeToCompletion() {
    assertEquals("Bad state before resumeToCompletion", SUSPENDED, state)

    setActionRequested
    currentStackFrame.resume

    waitUntilSuspended

    assertEquals("Bad state after resumeToCompletion", TERMINATED, state)
  }

  def terminate() {
    if ((state ne NOT_LAUNCHED) && (state ne TERMINATED)) {
      debugTarget.terminate
      waitUntilTerminated
      assertEquals("Bad state after terminate", TERMINATED, state)
    }
  }

  def continue() {
    setActionRequested
    currentStackFrame.resume
  }

  private def launch() {
    val launchConfiguration = DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(launchConfigurationFile)
    launchConfiguration.launch(ILaunchManager.DEBUG_MODE, null)
  }
  
  /**
   * Breakpoints are set asynchronously. It is fine in the UI, but it creates timing problems
   * while running test.
   * This method make sure there are no outstanding requests
   */
  def waitForBreakpointToBe(breakpoint: IBreakpoint, enabled: Boolean) {
    import scala.tools.eclipse.testsetup.SDTTestUtils._

    if (state ne NOT_LAUNCHED) {
      waitUntil(5000) {
        breakpoint.getMarker().getAttribute(BreakpointSupport.ATTR_VM_REQUESTS_ENABLED, !enabled) == enabled
      }
    }
  }
  def waitForBreakpointsToBeEnabled(breakpoint: IBreakpoint*) {
    breakpoint.foreach(waitForBreakpointToBe(_, true))
  }
  def waitForBreakpointsToBeDisabled(breakpoint: IBreakpoint*) {
    breakpoint.foreach(waitForBreakpointToBe(_, false))
  }

  // -----

  /**
   * Check that all threads have a suspended count of 0, except the one of the current thread, which should be 1
   */
  def checkThreadsState() {
    assertEquals("Bad state before checkThreadsState", SUSPENDED, state)

    val currentThread = currentStackFrame.stackFrame.thread
    import scala.collection.JavaConverters._
    debugTarget.virtualMachine.allThreads.asScala.foreach(thread =>
      assertEquals("Wrong suspended count", if (thread == currentThread) 1 else 0, thread.suspendCount))
  }

  def checkStackFrame(typeName: String, methodFullSignature: String, line: Int) {
    assertEquals("Bad state before checkStackFrame", SUSPENDED, state)

    assertEquals("Wrong typeName", typeName, currentStackFrame.stackFrame.location.declaringType.name)
    assertEquals("Wrong method/line" + currentStackFrame.getLineNumber, methodFullSignature, currentStackFrame.stackFrame.location.method.name + currentStackFrame.stackFrame.location.method.signature)
    assertEquals("Wrong line", line, currentStackFrame.getLineNumber)
  }
}