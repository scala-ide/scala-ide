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
import scala.actors.Future
import scala.tools.eclipse.debug.BaseDebuggerActor
import com.sun.jdi.ClassType

class ThreadNotSuspendedException extends Exception

object ScalaThread {
  def apply(target: ScalaDebugTarget, thread: ThreadReference): ScalaThread = {
    val scalaThread = new ScalaThread(target, thread) {
      override val companionActor = ScalaThreadActor(this)
    }
    scalaThread.fireCreationEvent()
    scalaThread
  }
}

/**
 * A thread in the Scala debug model.
 * This class is thread safe. Instances have be created through its companion object.
 */
abstract class ScalaThread private (target: ScalaDebugTarget, private[model] val threadRef: ThreadReference) extends ScalaDebugElement(target) with IThread {
  import ScalaThreadActor._

  // Members declared in org.eclipse.debug.core.model.IStep

  override def canStepInto: Boolean = suspended // TODO: need real logic
  override def canStepOver: Boolean = suspended // TODO: need real logic
  override def canStepReturn: Boolean = suspended // TODO: need real logic
  override def isStepping: Boolean = ???

  override def stepInto(): Unit = ScalaStepInto(stackFrames.head).step()
  override def stepOver(): Unit = ScalaStepOver(stackFrames.head).step()
  override def stepReturn(): Unit = ScalaStepReturn(stackFrames.head).step()

  // Members declared in org.eclipse.debug.core.model.ISuspendResume

  override def canResume: Boolean = suspended // TODO: need real logic
  override def canSuspend: Boolean = !suspended // TODO: need real logic
  override def isSuspended: Boolean = suspended // TODO: need real logic

  override def resume(): Unit = resumeFromScala(DebugEvent.CLIENT_REQUEST)
  override def suspend(): Unit = {
    threadRef.suspend()
    suspendedFromScala(DebugEvent.CLIENT_REQUEST)
  }

  // Members declared in org.eclipse.debug.core.model.IThread

  override def getBreakpoints: Array[IBreakpoint] = Array.empty // TODO: need real logic

  override def getName: String = {
    try {
      name = threadRef.name
    } catch {
      case e: ObjectCollectedException =>
        name = "<garbage collected>"
      case e: VMDisconnectedException =>
        name = "<disconnected>"
    }
    name
  }

  override def getPriority: Int = ???
  override def getStackFrames: Array[org.eclipse.debug.core.model.IStackFrame] = stackFrames.toArray
  override def getTopStackFrame: org.eclipse.debug.core.model.IStackFrame = stackFrames.headOption.getOrElse(null)
  override def hasStackFrames: Boolean = !stackFrames.isEmpty

  // ----

  // state
  @volatile
  private var suspended = false
  @volatile
  private var running = true
  
  /**
   * The current list of stack frames.
   * THE VALUE IS MODIFIED ONLY BY THE COMPANION ACTOR, USING METHODS DEFINED LOWER.
   */
  @volatile
  private var stackFrames= List[ScalaStackFrame]()

  // keep the last known name around, for when the vm is not available anymore
  @volatile
  private var name: String = null
  
  protected[debug] val companionActor: BaseDebuggerActor

  val isSystemThread: Boolean = {
    try Option(threadRef.threadGroup).exists(_.name == "system")
    catch {
      // some thread get created when a program terminates, and connection already closed
      case e: VMDisconnectedException => false
    }
  }

  def suspendedFromScala(eventDetail: Int): Unit = companionActor ! SuspendedFromScala(eventDetail)

  def resumeFromScala(eventDetail: Int): Unit = companionActor ! ResumeFromScala(None, eventDetail)

  def resumeFromScala(step: ScalaStep, eventDetail: Int): Unit = companionActor ! ResumeFromScala(Some(step), eventDetail)

  def terminatedFromScala(): Unit = dispose()

  /** Invoke the given method on the given instance with the given arguments.
   *
   *  This method should not be called directly.
   *  Use [[ScalaObjectReference.invokeMethod(String, ScalaThread, ScalaValue*)]]
   *  or [[ScalaObjectReference.invokeMethod(String, String, ScalaThread, ScalaValue*)]] instead.
   */
  def invokeMethod(objectReference: ObjectReference, method: Method, args: Value*): Value = {
    processMethodInvocationResult(companionActor !? InvokeMethod(objectReference, method, args.toList))
  }

  /** Invoke the given static method on the given type with the given arguments.
   *
   *  This method should not be called directly.
   *  Use [[ScalaClassType.invokeMethod(String, ScalaThread,ScalaValue*)]] instead.
   */
  def invokeStaticMethod(classType: ClassType, method: Method, args: Value*): Value = {
    processMethodInvocationResult(companionActor !? InvokeStaticMethod(classType, method, args.toList))
  }

  private def processMethodInvocationResult(res: Any): Value = res match {
    case Right(null) =>
      null
    case Right(res: Value) =>
      res
    case Left(e: Exception) =>
      throw e
  }

  /**
   * release all resources
   */
  def dispose() {
    running = false
    stackFrames= Nil
    companionActor ! TerminatedFromScala
  }
  
  /*
   * Methods used by the companion actor to update this object internal states
   * FOR THE COMPANION ACTOR ONLY.
   */

  /**
   * Set the this object internal states to suspended.
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def suspend(eventDetail: Int) {
    // FIXME: `threadRef.frames` should handle checked exception `IncompatibleThreadStateException`
    stackFrames= threadRef.frames.asScala.map(ScalaStackFrame(this, _)).toList
    suspended = true
    fireSuspendEvent(eventDetail)
  }

  /**
   * Set the this object internal states to resumed.
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def resume(eventDetail: Int) {
    suspended = false
    stackFrames= Nil
    fireResumeEvent(eventDetail)
  }

  /**
   * Rebind the Scala stack frame to the new underlying frames.
   * TO BE USED ONLY IF THE NUMBER OF FRAMES MATCHES
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def rebindScalaStackFrames() {
    // FIXME: `threadRef.frames` should handle checked exception `IncompatibleThreadStateException`
    // FIXME: Should check that `threadRef.frames == stackFrames` before zipping
    threadRef.frames.asScala.zip(stackFrames).foreach {
      case (jdiStackFrame, scalaStackFrame) => scalaStackFrame.rebind(jdiStackFrame)
    }    
  }

}

private[model] object ScalaThreadActor {
  case class SuspendedFromScala(eventDetail: Int)
  case class ResumeFromScala(step: Option[ScalaStep], eventDetail: Int)
  case class InvokeMethod(objectReference: ObjectReference, method: Method, args: List[Value])
  case class InvokeStaticMethod(classType: ClassType, method: Method, args: List[Value])
  case object TerminatedFromScala
  
  def apply(thread: ScalaThread): BaseDebuggerActor = {
    val actor = new ScalaThreadActor(thread)
    actor.start()
    actor
  }
}

/**
 * Actor used to manage a Scala thread. It keeps track of the existing stack frames, and of the execution status.
 * This class is thread safe. Instances are not to be created outside of the ScalaThread object.
 */
private[model] class ScalaThreadActor private(thread: ScalaThread) extends BaseDebuggerActor {
  import ScalaThreadActor._

  // step management
  private var currentStep: Option[ScalaStep] = None

  override protected def postStart(): Unit = link(thread.getDebugTarget.companionActor)
  
  override protected def behavior = {
    case SuspendedFromScala(eventDetail) =>
      currentStep.foreach(_.stop())
      currentStep = None
      thread.suspend(eventDetail)
    case ResumeFromScala(step, eventDetail) =>
      currentStep = step
      thread.resume(eventDetail)
      thread.threadRef.resume()
    case InvokeMethod(objectReference, method, args) =>
      reply(
        if (!thread.isSuspended) {
          Left(new ThreadNotSuspendedException())
        } else {
          try {
            import scala.collection.JavaConverters._
            // invoke the method
            // FIXME: Doesn't handle checked exceptions `InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException`
            val result = objectReference.invokeMethod(thread.threadRef, method, args.asJava, ObjectReference.INVOKE_SINGLE_THREADED)
            // update the stack frames
            thread.rebindScalaStackFrames()
            Right(result)
          } catch {
            case e: Exception =>
              Left(e)
          }
        })
    case InvokeStaticMethod(classType, method, args) =>
      reply(
        if (!thread.isSuspended) {
          Left(new ThreadNotSuspendedException())
        } else {
          try {
            import scala.collection.JavaConverters._
            // invoke the method
            // FIXME: Doesn't handle checked exceptions `InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException`
            val result = classType.invokeMethod(thread.threadRef, method, args.asJava, ObjectReference.INVOKE_SINGLE_THREADED)
            // update the stack frames
            thread.rebindScalaStackFrames()
            Right(result)
          } catch {
            case e: Exception =>
              Left(e)
          }
        })
    case TerminatedFromScala =>
      currentStep.foreach(_.stop())
      currentStep = None
      thread.fireTerminateEvent()
      poison()
  }
  
  override protected def preExit(): Unit = {
    // before shutting down the actor we need to unlink it from the `debugTarget` actor to prevent that normal termination of 
    // a `ScalaThread` leads to shutting down the whole debug session.
    unlink(thread.getDebugTarget.companionActor)
  }
}
