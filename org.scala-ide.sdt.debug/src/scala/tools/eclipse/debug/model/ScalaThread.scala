package scala.tools.eclipse.debug.model

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.tools.eclipse.debug.command.{ScalaStepOver, ScalaStep}

import org.eclipse.debug.core.model.{IThread, IBreakpoint}

import com.sun.jdi.ThreadReference

class ScalaThread(target: ScalaDebugTarget, val thread: ThreadReference) extends ScalaDebugElement(target) with IThread {

  // Members declared in org.eclipse.debug.core.model.IStep

  def canStepInto(): Boolean = false // TODO: need real logic
  def canStepOver(): Boolean = suspended // TODO: need real logic
  def canStepReturn(): Boolean = false // TODO: need real logic
  def isStepping(): Boolean = ???
  def stepInto(): Unit = ???

  def stepOver(): Unit = {
    // top stack frame
    currentStep = Some(ScalaStepOver(stackFrames.find(sf => true).get))
    currentStep.get.step
  }

  def stepReturn(): Unit = ???

  // Members declared in org.eclipse.debug.core.model.ISuspendResume

  def canResume(): Boolean = false // TODO: need real logic
  def canSuspend(): Boolean = false // TODO: need real logic
  def isSuspended(): Boolean = suspended // TODO: need real logic
  def resume(): Unit = ???
  def suspend(): Unit = ???

  // Members declared in org.eclipse.debug.core.model.IThread

  def getBreakpoints(): Array[IBreakpoint] = Array() // TODO: need real logic
  def getName(): String = thread.name
  def getPriority(): Int = ???
  def getStackFrames(): Array[org.eclipse.debug.core.model.IStackFrame] = stackFrames.toArray
  def getTopStackFrame(): org.eclipse.debug.core.model.IStackFrame = stackFrames.find(sf => true).getOrElse(null)
  def hasStackFrames(): Boolean = !stackFrames.isEmpty

  // ----

  var suspended = thread.isSuspended

  var currentStep: Option[ScalaStep] = None

  var stackFrames: List[ScalaStackFrame] = Nil

  fireCreationEvent

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

}