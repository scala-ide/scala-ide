package scala.tools.eclipse.debug

import scala.tools.eclipse.debug.model.{ScalaThread, ScalaStackFrame, ScalaDebugTarget}

import org.eclipse.core.resources.{ResourcesPlugin, IFile}
import org.eclipse.debug.core.{ILaunchManager, IDebugEventSetListener, DebugPlugin, DebugEvent}
import org.eclipse.jdt.debug.core.JDIDebugModel
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget
import org.hamcrest.CoreMatchers._
import org.junit.Assert._

class ScalaDebugTestSession(launchConfigurationFile: IFile) extends IDebugEventSetListener {

  object State extends Enumeration {
    type State = Value
    val NOT_LAUNCHED, RUNNING, SUSPENDED, TERMINATED = Value
  }
  import State._

  // from IDebugEventSetListener

  DebugPlugin.getDefault.addDebugEventListener(this)

  def handleDebugEvents(events: Array[DebugEvent]) {
    events.foreach(event =>
      event.getKind match {
        case DebugEvent.CREATE =>
          event.getSource match {
            case target: ScalaDebugTarget =>
              setLaunched(target)
            case _ =>
          }
        case DebugEvent.RESUME =>
          setRunning
        case DebugEvent.SUSPEND =>
          event.getSource match {
            case thread: ScalaThread =>
              setSuspended(thread.getTopStackFrame.asInstanceOf[ScalaStackFrame])
            case target: ScalaDebugTarget =>
              setSuspended(null)
            case _ =>
          }
        case DebugEvent.TERMINATE =>
          event.getSource match {
            case target: ScalaDebugTarget =>
              setTerminated
            case _ =>
          }
        case _ =>
      })
  }

  // ----

  def setLaunched(target: ScalaDebugTarget) {
    this.synchronized {
      debugTarget = target
      setRunning
    }
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

  // ----

  var state = NOT_LAUNCHED
  var debugTarget: ScalaDebugTarget = null
  var currentStackFrame: ScalaStackFrame = null

  def runToLine(typeName: String, breakpointLine: Int) {
    assertThat("Bad state before runToBreakpoint", state, anyOf(is(NOT_LAUNCHED), is(SUSPENDED)))

    val breakpoint = JDIDebugModel.createLineBreakpoint(ResourcesPlugin.getWorkspace.getRoot, typeName, breakpointLine, -1, -1, -1, true, null)

    if (state eq NOT_LAUNCHED) {
      launch()
    } else {
      resume()
    }

    waitUntilSuspended
    breakpoint.delete

    assertEquals("Bad state after runToBreakpoint", SUSPENDED, state)
  }

  def stepOver() {
    assertEquals("Bad state before stepOver", SUSPENDED, state)

    currentStackFrame.stepOver

    waitUntilSuspended

    assertEquals("Bad state after stepOver", SUSPENDED, state)
  }
  
  def resumeToCompletion() {
    assertEquals("Bad state before resumeToCompletion", SUSPENDED, state)

    resume

    waitUntilSuspended

    assertEquals("Bad state after resumeToCompletion", TERMINATED, state)
  }

  def terminate() {
    if ((state ne NOT_LAUNCHED) && (state ne TERMINATED)) {
      debugTarget.terminate
      waitUntilSuspended
      assertEquals("Bad state after terminate", TERMINATED, state)
    }
  }

  private def launch() {
    val launchConfiguration = DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(launchConfigurationFile)
    launchConfiguration.launch(ILaunchManager.DEBUG_MODE, null).getDebugTarget.asInstanceOf[JDIDebugTarget]
  }
  
  private def resume() {
    currentStackFrame.resume
  }

  // -----

  def checkStackFrame(typeName: String, methodFullSignature: String, line: Int) {
    assertEquals("Bad state before checkStackFrame", SUSPENDED, state)

    assertEquals("Wrong typeName", typeName, currentStackFrame.stackFrame.location.declaringType.name)
    assertEquals("Wrong method", methodFullSignature, currentStackFrame.stackFrame.location.method.name + currentStackFrame.stackFrame.location.method.signature)
    assertEquals("Wrong line", line, currentStackFrame.getLineNumber)
  }

}