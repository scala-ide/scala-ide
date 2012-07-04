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
import scala.actors.Actor
import scala.tools.eclipse.debug.ActorExit

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
  def canSuspend(): Boolean = !suspended // TODO: need real logic
  def isSuspended(): Boolean = suspended // TODO: need real logic
  def resume(): Unit = {
    thread.resume
    resumedFromScala(DebugEvent.CLIENT_REQUEST)
  }
  def suspend(): Unit = {
    thread.suspend
    suspendedFromScala(DebugEvent.CLIENT_REQUEST)
  }

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

  // event handling actor

  case class SuspendedFromScala(eventDetail: Int)
  case class ResumedFromScala(eventDetail: Int)
  case class InvokeMethod(objectReference: ObjectReference, method: Method, args: List[Value])
  case class TerminatedFromScala

  val eventActor= new Actor {
    start
    def act() {
      loop {
        react {
          case SuspendedFromScala(eventDetail) =>
            import scala.collection.JavaConverters._
            currentStep.foreach(_.stop)
            suspended = true
            stackFrames = thread.frames.asScala.map(new ScalaStackFrame(ScalaThread.this, _)).toList
            reply(None)
            fireChangeEvent(DebugEvent.CONTENT)
            fireSuspendEvent(eventDetail)
          case ResumedFromScala(eventDetail) =>
            suspended = false
            stackFrames = Nil
            fireResumeEvent(eventDetail)
          case InvokeMethod(objectReference, method, args) =>
            if (!suspended) {
              throw new Exception("Not suspended")
            } else {
              import scala.collection.JavaConverters._
              val result = objectReference.invokeMethod(thread, method, args.asJava, ObjectReference.INVOKE_SINGLE_THREADED)
              // update the stack frames
              thread.frames.asScala.iterator.zip(stackFrames.iterator).foreach(
                v => v._2.rebind(v._1))
              reply(result)
            }
          case TerminatedFromScala =>
            stackFrames = Nil
            fireTerminateEvent
            exit
        }
      }
    }
  }

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

  def suspendedFromScala(eventDetail: Int) {
    eventActor !? SuspendedFromScala(eventDetail)
  }

  def resumedFromScala(eventDetail: Int) {
    eventActor ! ResumedFromScala(eventDetail)
  }
  
  def terminatedFromScala() {
    eventActor ! TerminatedFromScala
  }

  def invokeMethod(objectReference: ObjectReference, method: Method, args: Value*): Value = {
    val future = eventActor !! InvokeMethod(objectReference, method, args.toList)

    future.inputChannel.receive {
      case value: Value =>
        value
    }
  }
  
  /**
   * release all resources
   */
  def dispose() {
    eventActor ! TerminatedFromScala
    currentStep.foreach(_.stop)
  }

}