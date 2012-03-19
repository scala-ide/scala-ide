package scala.tools.eclipse.debug.model

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.tools.eclipse.debug.command.{ ScalaStepOver, ScalaStep }
import org.eclipse.debug.core.model.{ IThread, IBreakpoint }
import com.sun.jdi.ThreadReference
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.ObjectCollectedException
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.Value
import com.sun.jdi.ObjectReference
import com.sun.jdi.Method
import scala.tools.eclipse.debug.command.ScalaStepInto
import scala.tools.eclipse.debug.command.ScalaStepReturn

/**
 * TODO: kill current step when thread terminates?
 */
class ScalaThread(target: ScalaDebugTarget, val thread: ThreadReference) extends ScalaDebugElement(target) with IThread {

  // Members declared in org.eclipse.debug.core.model.IStep

  def canStepInto(): Boolean = suspended // TODO: need real logic
  def canStepOver(): Boolean = suspended // TODO: need real logic
  def canStepReturn(): Boolean = suspended // TODO: need real logic
  def isStepping(): Boolean = ???
  def stepInto(): Unit = ScalaStepInto(stackFrames.head).step

  def stepOver(): Unit = {
    // top stack frame
    ScalaStepOver(stackFrames.head).step
  }

  def stepReturn(): Unit = {
    ScalaStepReturn(stackFrames.head).step
  }

  // Members declared in org.eclipse.debug.core.model.ISuspendResume

  def canResume(): Boolean = suspended // TODO: need real logic
  def canSuspend(): Boolean = false // TODO: need real logic
  def isSuspended(): Boolean = suspended // TODO: need real logic
  def resume(): Unit = {
    thread.resume
    resumedFromScala(DebugEvent.CLIENT_REQUEST)
  }
  def suspend(): Unit = ???

  // Members declared in org.eclipse.debug.core.model.IThread

  def getBreakpoints(): Array[IBreakpoint] = Array() // TODO: need real logic

  def getName(): String = {
    try {
      name = thread.name
    } catch {
      case e: ObjectCollectedException =>
        name = "<garbage collected>"
      case e: VMDisconnectedException =>
        name = "<disconnected>"
    }
    name
  }

  def getPriority(): Int = ???
  def getStackFrames(): Array[org.eclipse.debug.core.model.IStackFrame] = stackFrames.toArray
  def getTopStackFrame(): org.eclipse.debug.core.model.IStackFrame = stackFrames.headOption.getOrElse(null)
  def hasStackFrames(): Boolean = !stackFrames.isEmpty

  // ----

  // state
  var suspended = false

  var stackFrames: List[ScalaStackFrame] = Nil

  // initialize name
  private var name: String = null

  val isSystemThread: Boolean = try {
    Option(thread.threadGroup).exists(_.name == "system")
  } catch {
    case e: VMDisconnectedException =>
      // some thread get created when a program terminates, and connection already closed
      false
  }

  fireCreationEvent

  // step management
  var currentStep: Option[ScalaStep] = None

  def suspendedFromJava(eventDetail: Int) {
    import scala.collection.JavaConverters._
    currentStep.foreach(_.stop)
    suspended = true

    stackFrames = thread.frames.asScala.map(new ScalaStackFrame(this, _)).toList
    fireSuspendEvent(eventDetail)
  }

  def suspendedFromScala(eventDetail: Int) {
    import scala.collection.JavaConverters._
    suspended = true

    stackFrames = thread.frames.asScala.map(new ScalaStackFrame(this, _)).toList
    fireSuspendEvent(eventDetail)
  }

  def resumedFromScala(eventDetail: Int) {
    suspended = false
    stackFrames = Nil
    fireResumeEvent(eventDetail)
  }

  def updateStackFramesAfterInvocation() {
    import scala.collection.JavaConverters._
    thread.frames.asScala.iterator.zip(stackFrames.iterator).foreach(
      v => v._2.rebind(v._1))
  }

  // ----

  def invokeMethod(objectReference: ObjectReference, method: Method, args: Value*): Value = {
    import scala.collection.JavaConverters._
    val result = objectReference.invokeMethod(thread, method, args.asJava, 0)
    updateStackFramesAfterInvocation()
    result
  }

}