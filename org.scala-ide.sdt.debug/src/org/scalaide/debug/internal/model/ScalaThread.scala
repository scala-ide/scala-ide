package org.scalaide.debug.internal.model

import scala.collection.JavaConverters.asScalaBufferConverter

import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.debug.core.model.IStackFrame
import org.eclipse.debug.core.model.IThread
import org.scalaide.debug.internal.BaseDebuggerActor
import org.scalaide.debug.internal.JDIUtil._
import org.scalaide.debug.internal.command.ScalaStep
import org.scalaide.debug.internal.command.ScalaStepInto
import org.scalaide.debug.internal.command.ScalaStepOver
import org.scalaide.debug.internal.command.ScalaStepReturn
import org.scalaide.debug.internal.preferences.HotCodeReplacePreferences
import org.scalaide.logging.HasLogger
import org.scalaide.util.Utils.jdiSynchronized

import com.sun.jdi.ClassType
import com.sun.jdi.IncompatibleThreadStateException
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference
import com.sun.jdi.VMCannotBeModifiedException
import com.sun.jdi.Value

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
abstract class ScalaThread private(target: ScalaDebugTarget, val threadRef: ThreadReference)
    extends ScalaDebugElement(target) with IThread with HasLogger {
  import ScalaThreadActor._
  import BaseDebuggerActor._

  // Members declared in org.eclipse.debug.core.model.IStep

  override def canStepInto: Boolean = canStep
  override def canStepOver: Boolean = canStep
  override def canStepReturn: Boolean = canStep
  override def isStepping: Boolean = ???
  private def canStep = suspended && !target.isPerformingHotCodeReplace

  override def stepInto(): Unit = {
    for (head <- stackFrames.headOption) {
      wrapJDIException("Exception while performing `step into`") { ScalaStepInto(head).step() }
    }
  }
  override def stepOver(): Unit = {
    for (head <- stackFrames.headOption) {
      wrapJDIException("Exception while performing `step over`") { ScalaStepOver(head).step() }
    }
  }
  override def stepReturn(): Unit = {
    for (head <- stackFrames.headOption) {
      wrapJDIException("Exception while performing `step return`") { ScalaStepReturn(head).step() }
    }
  }

  // Members declared in org.eclipse.debug.core.model.ISuspendResume

  override def canResume: Boolean = suspended && !target.isPerformingHotCodeReplace
  override def canSuspend: Boolean = !suspended // TODO: need real logic
  override def isSuspended: Boolean = util.Try(threadRef.isSuspended).getOrElse(false)

  override def resume(): Unit = resumeFromScala(DebugEvent.CLIENT_REQUEST)
  override def suspend(): Unit = {
    (safeThreadCalls(()) or wrapJDIException("Exception while retrieving suspending stack frame")) {
      threadRef.suspend()
      suspendedFromScala(DebugEvent.CLIENT_REQUEST)
    }
  }

  // Members declared in org.eclipse.debug.core.model.IThread

  override def getBreakpoints: Array[IBreakpoint] = Array.empty // TODO: need real logic

  override def getName: String = {
    (safeThreadCalls("Error retrieving name") or wrapJDIException("Exception while retrieving stack frame's name")) {
      name = threadRef.name
      name
    }
  }

  override def getPriority: Int = ???
  override def getStackFrames: Array[IStackFrame] = stackFrames.toArray
  final def getScalaStackFrames: List[ScalaStackFrame] = stackFrames
  override def getTopStackFrame: ScalaStackFrame = stackFrames.headOption.getOrElse(null)
  override def hasStackFrames: Boolean = !stackFrames.isEmpty

  // ----

  // state
  @volatile
  private var suspended = false

  /**
   * The current list of stack frames.
   * THE VALUE IS MODIFIED ONLY BY THE COMPANION ACTOR, USING METHODS DEFINED LOWER.
   */
  @volatile
  private var stackFrames: List[ScalaStackFrame] = Nil

  // keep the last known name around, for when the vm is not available anymore
  @volatile
  private var name: String = null

  protected[debug] val companionActor: BaseDebuggerActor

  val isSystemThread: Boolean = {
    safeThreadCalls(false) { Option(threadRef.threadGroup).exists(_.name == "system") }
  }

  def suspendedFromScala(eventDetail: Int): Unit = companionActor ! SuspendedFromScala(eventDetail)

  def resumeFromScala(eventDetail: Int): Unit = companionActor ! ResumeFromScala(None, eventDetail)

  def resumeFromScala(step: ScalaStep, eventDetail: Int): Unit = companionActor ! ResumeFromScala(Some(step), eventDetail)

  def terminatedFromScala(): Unit = dispose()

  /**
   * Invoke the given method on the given instance with the given arguments.
   *
   *  This method should not be called directly.
   *  Use [[ScalaObjectReference.invokeMethod(String, ScalaThread, ScalaValue*)]]
   *  or [[ScalaObjectReference.invokeMethod(String, String, ScalaThread, ScalaValue*)]] instead.
   */
  def invokeMethod(objectReference: ObjectReference, method: Method, args: Value*): Value = {
    processMethodInvocationResult(syncSend(companionActor, InvokeMethod(objectReference, method, args.toList)))
  }

  /**
   * Invoke the given static method on the given type with the given arguments.
   *
   *  This method should not be called directly.
   *  Use [[ScalaClassType.invokeMethod(String, ScalaThread,ScalaValue*)]] instead.
   */
  def invokeStaticMethod(classType: ClassType, method: Method, args: Value*): Value = {
    processMethodInvocationResult(syncSend(companionActor, InvokeStaticMethod(classType, method, args.toList)))
  }

  private def stepIntoFrame(stackFrame: => ScalaStackFrame): Unit =
    wrapJDIException("Exception while performing `step into`") { ScalaStepInto(stackFrame).step() }

  /**
   * It's not possible to drop the bottom stack frame. Moreover all dropped frames and also
   * the one below the target frame can't be native.
   *
   * @param frame frame which we'd like to drop and step into it once again
   * @param relatedToHcr when dropping frames automatically after Hot Code Replace we need a bit different processing
   */
  private[model] def canDropToFrame(frame: ScalaStackFrame, relatedToHcr: Boolean = false): Boolean = {
    val frames = stackFrames
    val indexOfFrame = frames.indexOf(frame)

    val atLeastLastButOne = frames.size >= indexOfFrame + 2
    val canDropObsoleteFrames = HotCodeReplacePreferences.allowToDropObsoleteFramesManually

    // Obsolete frames are marked as native so we have to ignore isNative when user really wants to drop obsolete frames manually.
    // User has to be aware that it can cause problems when he'd really try to drop the native frame marked as obsolete.
    def isNativeAndIsNotObsoleteWhenObsoleteAllowed(f: ScalaStackFrame) = f.isNative && !(canDropObsoleteFrames && f.isObsolete)

    def notNative = !frames.take(indexOfFrame + 2).exists(isNativeAndIsNotObsoleteWhenObsoleteAllowed)
    canPopFrames && atLeastLastButOne && (relatedToHcr || (!target.isPerformingHotCodeReplace && notNative))
  }

  private[model] def canPopFrames: Boolean = isSuspended && target.canPopFrames

  private[model] def dropToFrame(frame: ScalaStackFrame): Unit = companionActor ! DropToFrame(frame)

  /**
   * Removes all top stack frames starting from a given one and performs StepInto to reach the given frame again.
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def dropToFrameInternal(frame: ScalaStackFrame, relatedToHcr: Boolean = false): Unit =
    (safeThreadCalls(()) or wrapJDIException("Exception while performing Drop To Frame"))(jdiSynchronized {
      if (canDropToFrame(frame, relatedToHcr)) {
        val frames = stackFrames
        val startFrameForStepInto = frames(frames.indexOf(frame) + 1)
        threadRef.popFrames(frame.stackFrame)
        stepIntoFrame(startFrameForStepInto)
      }
    })

  /**
   * @param shouldFireChangeEvent fire an event after refreshing frames to refresh also UI elements
   */
  def refreshStackFrames(shouldFireChangeEvent: Boolean): Unit =
    companionActor ! RebindStackFrames(shouldFireChangeEvent)

  private[internal] def updateStackFramesAfterHcr(msg: ScalaDebugTarget.UpdateStackFramesAfterHcr): Unit = companionActor ! msg

  private def processMethodInvocationResult(res: Option[Any]): Value = res match {
    case Some(Right(null)) =>
      null
    case Some(Right(res: Value)) =>
      res
    case Some(Left(e: Exception)) =>
      throw e
    case None =>
      null
    case Some(v) =>
      // to make the match exhaustive. Should not happen.
      logger.error(s"Not recognized method invocation result: $v")
      null
  }

  /**
   * release all resources
   */
  def dispose(): Unit = {
    stackFrames = Nil
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
  private[model] def suspend(eventDetail: Int) = {
    (safeThreadCalls(()) or wrapJDIException("Exception while suspending thread")) {
      // FIXME: `threadRef.frames` should handle checked exception `IncompatibleThreadStateException`
      stackFrames = jdiSynchronized {
        threadRef.frames
      }.asScala.zipWithIndex.map {
        case (frame, index) =>
          ScalaStackFrame(this, frame, index)
      }(collection.breakOut)
      suspended = true
      fireSuspendEvent(eventDetail)
    }
  }

  /**
   * Set the this object internal states to resumed.
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def resume(eventDetail: Int): Unit = {
    suspended = false
    stackFrames = Nil
    fireResumeEvent(eventDetail)
  }

  /**
   * Rebind the Scala stack frame to the new underlying frames.
   * TO BE USED ONLY IF THE NUMBER OF FRAMES MATCHES
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def rebindScalaStackFrames(): Unit = (safeThreadCalls(()) or wrapJDIException("Exception while rebinding stack frames")) {
    rebindFrames()
  }

  private def rebindFrames(): Unit = jdiSynchronized {
    // FIXME: Should check that `threadRef.frames == stackFrames` before zipping
    threadRef.frames.asScala.zip(stackFrames).foreach {
      case (jdiStackFrame, scalaStackFrame) => scalaStackFrame.rebind(jdiStackFrame)
    }
  }

  /**
   * Refreshes frames and optionally drops affected ones.
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def updateScalaStackFramesAfterHcr(dropAffectedFrames: Boolean): Unit =
    (safeThreadCalls(()) or wrapJDIException("Exception while rebinding stack frames")) {
      // obsolete frames will be marked as native so we need to check this before we'll rebind frames
      val nativeFrameIndex = stackFrames.indexWhere(_.isNative)

      rebindFrames()
      if (dropAffectedFrames) {
        val topNonNativeFrames =
          if (nativeFrameIndex == -1) stackFrames
          else stackFrames.take(nativeFrameIndex - 1) // we can't drop to native frame and also the first older frame can't be native
        val obsoleteFrames = topNonNativeFrames.filter(_.isObsolete)
        for (frame <- obsoleteFrames.lastOption)
          dropToFrameInternal(frame, relatedToHcr = true)
      }
      fireChangeEvent(DebugEvent.CONTENT)
    }

  import scala.util.control.Exception
  import Exception.Catch

  /** Wrap calls to the underlying VM thread reference to handle exceptions gracefully. */
  private def safeThreadCalls[A](defaultValue: A): Catch[A] =
    (safeVmCalls(defaultValue)
      or Exception.failAsValue(
        classOf[IncompatibleThreadStateException],
        classOf[VMCannotBeModifiedException])(defaultValue))
}

private[model] object ScalaThreadActor {
  case class SuspendedFromScala(eventDetail: Int)
  case class ResumeFromScala(step: Option[ScalaStep], eventDetail: Int)
  case class InvokeMethod(objectReference: ObjectReference, method: Method, args: List[Value])
  case class InvokeStaticMethod(classType: ClassType, method: Method, args: List[Value])
  case class DropToFrame(frame: ScalaStackFrame)
  case object TerminatedFromScala
  case class RebindStackFrames(shouldFireChangeEvent: Boolean)

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
private[model] class ScalaThreadActor private (thread: ScalaThread) extends BaseDebuggerActor {
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
    case DropToFrame(frame) =>
      thread.dropToFrameInternal(frame)
    case RebindStackFrames(shouldFireChangeEvent) =>
      if (thread.isSuspended) {
        thread.rebindScalaStackFrames()
        if (shouldFireChangeEvent) thread.fireChangeEvent(DebugEvent.CONTENT)
      }
    case ScalaDebugTarget.UpdateStackFramesAfterHcr(dropAffectedFrames) =>
      if (thread.isSuspended) thread.updateScalaStackFramesAfterHcr(dropAffectedFrames)
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
