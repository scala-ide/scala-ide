package org.scalaide.debug.internal

import org.scalaide.debug.internal.model.ScalaThread
import org.scalaide.debug.internal.model.ScalaStackFrame
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.model.ScalaDebugElement
import org.scalaide.debug.internal.model.ScalaValue
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.resources.IFile
import org.eclipse.debug.core.ILaunchManager
import org.eclipse.debug.core.IDebugEventSetListener
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.jdt.debug.core.JDIDebugModel
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget
import org.hamcrest.CoreMatchers._
import org.hamcrest.Matcher
import org.junit.Assert._
import org.eclipse.debug.core.model.DebugElement
import org.eclipse.jdt.internal.debug.core.model.JDIDebugElement
import org.scalaide.logging.HasLogger
import org.eclipse.debug.core.ILaunchConfiguration
import org.scalaide.debug.internal.breakpoints.BreakpointSupport
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jdt.debug.core.IJavaBreakpoint
import org.junit.Assert
import org.eclipse.debug.ui.contexts.DebugContextEvent

object EclipseDebugEvent {
  def unapply(event: DebugEvent): Option[(Int, AnyRef)] = Some((event.getKind, event.getSource()))
}

object ScalaDebugTestSession {
  // function doing nothing
  val Noop = () => ()

  def addDebugEventListener(f: PartialFunction[DebugEvent, Unit]): IDebugEventSetListener = {
    val debugEventListener= new IDebugEventSetListener {
      def handleDebugEvents(events: Array[DebugEvent]) {
        events.foreach(f orElse {case _ =>})
      }
    }
    DebugPlugin.getDefault.addDebugEventListener(debugEventListener)
    debugEventListener
  }

  def apply(launchConfiguration: ILaunchConfiguration): ScalaDebugTestSession = {
    val session = new ScalaDebugTestSession(launchConfiguration)
    session.skipAllBreakpoints(false)
    session
  }

  def apply(launchConfigurationFile: IFile): ScalaDebugTestSession =
    apply(DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(launchConfigurationFile))
}

class ScalaDebugTestSession private(launchConfiguration: ILaunchConfiguration) extends HasLogger {
  // 60s should be enough even for Jenkins builds running under high-load
  // (increased from 10s)
  val TIMEOUT = 60000

  object State extends Enumeration {
    type State = Value
    val ACTION_REQUESTED, NOT_LAUNCHED, RUNNING, SUSPENDED, TERMINATED = Value
  }
  import State._

  val debugEventListener = ScalaDebugTestSession.addDebugEventListener {
    case EclipseDebugEvent(DebugEvent.CREATE, target: ScalaDebugTarget) =>
      setLaunched(target)
    case EclipseDebugEvent(DebugEvent.RESUME, x) =>
      setRunning()
    case EclipseDebugEvent(DebugEvent.SUSPEND, thread: ScalaThread) =>
      setSuspended(thread.getTopStackFrame.asInstanceOf[ScalaStackFrame])
    case EclipseDebugEvent(DebugEvent.SUSPEND, target: ScalaDebugTarget) if target == debugTarget =>
      setSuspended(null)
    case EclipseDebugEvent(DebugEvent.TERMINATE, target: ScalaDebugTarget) if target == debugTarget =>
      setTerminated()
  }

  def setLaunched(target: ScalaDebugTarget) {
    this.synchronized {
      debugTarget = target
      setRunning()
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
      val selection = new StructuredSelection(stackFrame)
      ScalaDebugger.updateCurrentThreadAndStackFrame(selection)
      state = SUSPENDED
      logger.info("SUSPENDED at: %s:%d".format(stackFrame.getMethodFullName, stackFrame.getLineNumber))
      this.notify
    }
  }

  def setTerminated() {
    this.synchronized {
      state = TERMINATED
      this.notify()
    }
  }

  def waitUntilSuspended() {
    this.synchronized {
      while (state != SUSPENDED && state != TERMINATED)
        this.wait()
    }
  }

  def waitUntilTerminated() {
    this.synchronized {
      if (state != TERMINATED)
        this.wait()
    }
  }

  // ----

  var state = NOT_LAUNCHED
  var debugTarget: ScalaDebugTarget = null
  var currentStackFrame: ScalaStackFrame = null

  /**
   * Add a breakpoint at the specified location,
   * start or launch the session,
   * and wait until the application is suspended
   */
  def runToLine(typeName: String, breakpointLine: Int) {
    runToLine(typeName, breakpointLine, ScalaDebugTestSession.Noop)
  }

  /**
   * Add a breakpoint at the specified location,
   * start or launch the session,
   * perform the additional action,
   * and wait until the application is suspended
   */
  def runToLine[T](typeName: String, breakpointLine: Int, additionalAction: () => T): T = {
    assertThat("Bad state before runToBreakpoint", state, anyOf[State.Value](is[State.Value](NOT_LAUNCHED), is[State.Value](SUSPENDED)))

    val breakpoint = addLineBreakpoint(typeName, breakpointLine)

    if (state eq NOT_LAUNCHED) {
      launch()
    } else {
      setActionRequested
      currentStackFrame.resume
    }

    val actionResult = additionalAction()

    waitUntilSuspended
    removeBreakpoint(breakpoint)

    assertEquals("Bad state after runToBreakpoint", SUSPENDED, state)

    actionResult
  }

  /**
   * Add a breakpoint in the given type and its nested types at the given line (1 based)
   */
  def addLineBreakpoint(typeName: String, breakpointLine: Int): IJavaLineBreakpoint = {
    val breakpoint = JDIDebugModel.createLineBreakpoint(ResourcesPlugin.getWorkspace.getRoot, typeName, breakpointLine, /*char start*/ -1, /*char end*/ -1, /*hit count*/ -1, /*register*/ true, /*attributes*/ null)
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
      debugTarget.terminate()
      waitUntilTerminated
      assertEquals("Bad state after terminate", TERMINATED, state)
    }
    DebugPlugin.getDefault().removeDebugEventListener(debugEventListener)
  }

  def resumetoSuspension() {
    assertEquals("Bad state before resumeToCompletion", SUSPENDED, state)

    setActionRequested
    currentStackFrame.resume

    waitUntilSuspended

    assertEquals("Bad state after resumeToCompletion", SUSPENDED, state)
  }

  def disconnect() {
    if ((state ne NOT_LAUNCHED) && (state ne TERMINATED)) {
      debugTarget.disconnect()
      waitUntilTerminated
      assertEquals("Bad state after terminate", TERMINATED, state)
    }
  }

  def launch() {
    launchConfiguration.launch(ILaunchManager.DEBUG_MODE, null)
  }

  /**
   * Breakpoints are set asynchronously. It is fine in the UI, but it creates timing problems
   * while running test.
   * This method make sure there are no outstanding requests
   */
  def waitForBreakpointToBe(breakpoint: IBreakpoint, enabled: Boolean) {
    import org.scalaide.core.testsetup.SDTTestUtils._

    if (state ne NOT_LAUNCHED) {
      debugTarget.breakpointManager.waitForAllCurrentEvents()
      waitUntil(TIMEOUT) {
        debugTarget.breakpointManager.getBreakpointRequestState(breakpoint) match {
          case Some(state) =>
            state == enabled
          case _ =>
            Assert.fail("No BreakpointSupportActor exist for the passed breakpoint")
            false
        }
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

  // access data in the current stackframe

  /** Return the current value of a local variable.
   */
  def getLocalVariable(name: String): ScalaValue = {
    assertEquals("Bad state before getLocalVariable", SUSPENDED, state)

    currentStackFrame.getVariables.find(_.getName == name).get.getValue.asInstanceOf[ScalaValue]
  }

  def skipAllBreakpoints(enabled: Boolean): Unit =
    DebugPlugin.getDefault().getBreakpointManager().setEnabled(!enabled)
}