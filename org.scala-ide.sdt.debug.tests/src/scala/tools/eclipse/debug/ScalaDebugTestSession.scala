package scala.tools.eclipse.debug

import scala.tools.eclipse.debug.model.{ ScalaThread, ScalaStackFrame, ScalaDebugTarget }
import org.eclipse.core.resources.{ ResourcesPlugin, IFile }
import org.eclipse.debug.core.{ ILaunchManager, IDebugEventSetListener, DebugPlugin, DebugEvent }
import org.eclipse.jdt.debug.core.JDIDebugModel
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget
import org.hamcrest.CoreMatchers._
import org.junit.Assert._
import org.eclipse.debug.core.model.DebugElement
import scala.tools.eclipse.debug.model.ScalaDebugElement
import org.eclipse.jdt.internal.debug.core.model.JDIDebugElement

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

class ScalaDebugTestSession(launchConfigurationFile: IFile) extends IDebugEventSetListener {

  object State extends Enumeration {
    type State = Value
    val ACTION_REQUESTED, NOT_LAUNCHED, RUNNING, SUSPENDED, TERMINATED = Value
  }
  import State._

  // from IDebugEventSetListener

  DebugPlugin.getDefault.addDebugEventListener(this)

  def handleDebugEvents(events: Array[DebugEvent]) {

    events.foreach(
      _ match {
        case EclipseDebugEvent(DebugEvent.CREATE, target: ScalaDebugTarget) =>
          setLaunched(target)
        case EclipseDebugEvent(DebugEvent.RESUME, x) =>
          setRunning
        case EclipseDebugEvent(DebugEvent.SUSPEND, thread: ScalaThread) =>
          setSuspended(thread.getTopStackFrame.asInstanceOf[ScalaStackFrame])
        case EclipseDebugEvent(DebugEvent.SUSPEND, target: ScalaDebugTarget) =>
          setSuspended(null)
        case EclipseDebugEvent(DebugEvent.TERMINATE, target: ScalaDebugTarget) =>
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
      this.notify
    }
  }

  def setTerminated() {
    this.synchronized {
      state = TERMINATED
      debugTarget = null
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

    val breakpoint = JDIDebugModel.createLineBreakpoint(ResourcesPlugin.getWorkspace.getRoot, typeName, breakpointLine, -1, -1, -1, true, null)
    waitForBreakpointsToBeInstalled

    if (state eq NOT_LAUNCHED) {
      launch()
    } else {
      setActionRequested
      currentStackFrame.resume
    }

    waitUntilSuspended
    breakpoint.delete

    assertEquals("Bad state after runToBreakpoint", SUSPENDED, state)
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

  private def launch() {
    val launchConfiguration = DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(launchConfigurationFile)
    launchConfiguration.launch(ILaunchManager.DEBUG_MODE, null)
  }
  
  /**
   * Breakpoints are set asynchronously. It is fine in the UI, but it create timing problems
   * while running test.
   * This method make sure there are no outstanding requests
   */
  private def waitForBreakpointsToBeInstalled() {
    if (state ne NOT_LAUNCHED) {
      debugTarget.breakpointManager.waitForAllCurrentEvents
    }
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
    assertEquals("Wrong method", methodFullSignature, currentStackFrame.stackFrame.location.method.name + currentStackFrame.stackFrame.location.method.signature)
    assertEquals("Wrong line", line, currentStackFrame.getLineNumber)
  }

}