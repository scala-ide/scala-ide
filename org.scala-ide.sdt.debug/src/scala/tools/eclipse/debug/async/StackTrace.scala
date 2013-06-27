package scala.tools.eclipse.debug.async

import com.sun.jdi.Value
import org.eclipse.debug.core.model.IValue
import org.eclipse.debug.core.model.IStackFrame
import org.eclipse.debug.core.model.IDebugTarget
import org.eclipse.debug.core.model.DebugElement
import org.eclipse.debug.core.model.IVariable
import scala.tools.eclipse.debug.ScalaDebugPlugin
import org.eclipse.debug.core.model.IThread

case class Location(sourceName: String, declaringTypeName: String, line: Int) {
  override def toString(): String =
    s"$declaringTypeName line: $line"
}

// strict model of a captured stacktrace
case class AsyncStackTrace(frames: Seq[AsyncStackFrame])
case class AsyncStackFrame(locals: Seq[AsyncLocalVariable], location: Location)(val dbgTarget: IDebugTarget) extends DebugElement(dbgTarget) with IStackFrame {
  override def getName(): String = location.declaringTypeName
  override def getLineNumber(): Int = location.line
  override def getCharEnd() = -1
  override def getCharStart() = -1
  override def getThread(): IThread = NullThread
  override def getRegisterGroups() = null
  def getModelIdentifier(): String = ScalaDebugPlugin.id

  /** Return the source path based on source name and the package.
   *  Segments are separated by '/'.
   *
   *  @throws DebugException
   */
  def getSourcePath(): String = {
    // we shoudn't use location#sourcePath, as it is platform dependent
    location.declaringTypeName.split('.').init match {
      case Array() => location.sourceName
      case packageSegments =>
        packageSegments.mkString("", "/", "/") + location.sourceName
    }
  }

  // Members declared in org.eclipse.debug.core.model.IStackFrame
  def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = locals.toArray[IVariable]
  def hasRegisterGroups(): Boolean = false
  def hasVariables(): Boolean = locals.nonEmpty
  // Members declared in org.eclipse.debug.core.model.IStep
  def canStepInto(): Boolean = false
  def canStepOver(): Boolean = false
  def canStepReturn(): Boolean = false
  def isStepping(): Boolean = false
  def stepInto(): Unit = ???
  def stepOver(): Unit = ???
  def stepReturn(): Unit = ???
  // Members declared in org.eclipse.debug.core.model.ISuspendResume
  def canResume(): Boolean = false
  def canSuspend(): Boolean = false
  def isSuspended(): Boolean = false
  def resume(): Unit = ???
  def suspend(): Unit = ???
  // Members declared in org.eclipse.debug.core.model.ITerminate
  def canTerminate(): Boolean = false
  def isTerminated(): Boolean = false
  def terminate(): Unit = ???

  object NullThread extends DebugElement(dbgTarget) with IThread {
    // Members declared in org.eclipse.debug.core.model.IDebugElement
    def getModelIdentifier(): String = ScalaDebugPlugin.id
    // Members declared in org.eclipse.debug.core.model.IStep
    def canStepInto(): Boolean = ???
    def canStepOver(): Boolean = ???
    def canStepReturn(): Boolean = ???
    def isStepping(): Boolean = ???
    def stepInto(): Unit = ???
    def stepOver(): Unit = ???
    def stepReturn(): Unit = ???
    // Members declared in org.eclipse.debug.core.model.ISuspendResume
    def canResume(): Boolean = ???
    def canSuspend(): Boolean = ???
    def isSuspended(): Boolean = ???
    def resume(): Unit = ???
    def suspend(): Unit = ???
    // Members declared in org.eclipse.debug.core.model.ITerminate
    def canTerminate(): Boolean = ???
    def isTerminated(): Boolean = ???
    def terminate(): Unit = ???
    // Members declared in org.eclipse.debug.core.model.IThread
    def getBreakpoints(): Array[org.eclipse.debug.core.model.IBreakpoint] = ???
    def getName(): String = "<dummy>"
    def getPriority(): Int = ???
    def getStackFrames(): Array[org.eclipse.debug.core.model.IStackFrame] = Array(getTopStackFrame)
    def getTopStackFrame(): org.eclipse.debug.core.model.IStackFrame = AsyncStackFrame.this
    def hasStackFrames(): Boolean = true
  }
}
case class AsyncLocalVariable(name: String, value: IValue)(val dbgTarget: IDebugTarget) extends DebugElement(dbgTarget) with IVariable {
  // Members declared in org.eclipse.debug.core.model.IDebugElement
  def getModelIdentifier(): String = ScalaDebugPlugin.id
  // Members declared in org.eclipse.debug.core.model.IValueModification
  def setValue(x$1: org.eclipse.debug.core.model.IValue): Unit = ???
  def setValue(x$1: String): Unit = ???
  def supportsValueModification(): Boolean = false
  def verifyValue(x$1: org.eclipse.debug.core.model.IValue): Boolean = true
  def verifyValue(x$1: String): Boolean = true

  // Members declared in org.eclipse.debug.core.model.IVariable
  def getName(): String = name
  def getReferenceTypeName(): String = "<unknown type>"
  def getValue(): org.eclipse.debug.core.model.IValue = value
  def hasValueChanged(): Boolean = false
}