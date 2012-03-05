package scala.tools.eclipse.debug.model

import scala.collection.JavaConverters.asScalaBufferConverter
import org.eclipse.debug.core.model.IStackFrame
import com.sun.jdi.StackFrame
import com.sun.jdi.AbsentInformationException

class ScalaStackFrame(val thread: ScalaThread, val stackFrame: StackFrame) extends ScalaDebugElement(thread.getScalaDebugTarget) with IStackFrame {

  // Members declared in org.eclipse.debug.core.model.IStackFrame

  def getCharEnd(): Int = -1
  def getCharStart(): Int = -1
  def getLineNumber(): Int = stackFrame.location.lineNumber // TODO: cache data ?
  def getName(): String = stackFrame.location.declaringType.name // TODO: cache data ?
  def getRegisterGroups(): Array[org.eclipse.debug.core.model.IRegisterGroup] = ???
  def getThread(): org.eclipse.debug.core.model.IThread = thread
  def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = variables.toArray // TODO: need real logic
  def hasRegisterGroups(): Boolean = ???
  def hasVariables(): Boolean = ???

  // Members declared in org.eclipse.debug.core.model.IStep

  def canStepInto(): Boolean = false // TODO: need real logic
  def canStepOver(): Boolean = true // TODO: need real logic
  def canStepReturn(): Boolean = false // TODO: need real logic
  def isStepping(): Boolean = ???
  def stepInto(): Unit = ???
  def stepOver(): Unit = thread.stepOver
  def stepReturn(): Unit = ???

  // Members declared in org.eclipse.debug.core.model.ISuspendResume

  def canResume(): Boolean = false // TODO: need real logic
  def canSuspend(): Boolean = false // TODO: need real logic
  def isSuspended(): Boolean = true // TODO: need real logic
  def resume(): Unit = ???
  def suspend(): Unit = ???

  // ---

  fireCreationEvent

  val variables: Seq[ScalaLocalVariable] = {
    import scala.collection.JavaConverters._
    try {
      stackFrame.visibleVariables.asScala.map(new ScalaLocalVariable(_, this))
    } catch {
      case e: AbsentInformationException => Seq()
    }
  }

  def getSourceName(): String = stackFrame.location.sourceName

}