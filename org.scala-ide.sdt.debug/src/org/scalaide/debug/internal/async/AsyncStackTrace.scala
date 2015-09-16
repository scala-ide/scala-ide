package org.scalaide.debug.internal.async

import org.eclipse.debug.core.model.DebugElement
import org.eclipse.debug.core.model.IDebugTarget
import org.eclipse.debug.core.model.IStackFrame
import org.eclipse.debug.core.model.IThread
import org.eclipse.debug.core.model.IValue
import org.eclipse.debug.core.model.IVariable
import org.scalaide.debug.internal.ScalaDebugPlugin

case class Location(sourceName: String, declaringTypeName: String, line: Int) {
  override def toString(): String =
    s"$declaringTypeName at line: $line"
}

case class AsyncStackTrace(frames: Seq[AsyncStackFrame])

case class AsyncStackFrame
    (locals: Seq[AsyncLocalVariable], location: Location)
    (val dbgTarget: IDebugTarget)
      extends DebugElement(dbgTarget)
      with IStackFrame {

  override def getName(): String = location.declaringTypeName
  override def getLineNumber(): Int = location.line
  override def getCharEnd() = -1
  override def getCharStart() = -1
  override def getThread(): IThread = NullThread
  override def getRegisterGroups() = null
  override def getModelIdentifier(): String = ScalaDebugPlugin.id

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
  override def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = locals.toArray[IVariable]
  override def hasRegisterGroups(): Boolean = false
  override def hasVariables(): Boolean = locals.nonEmpty
  // Members declared in org.eclipse.debug.core.model.IStep
  override def canStepInto(): Boolean = false
  override def canStepOver(): Boolean = false
  override def canStepReturn(): Boolean = false
  override def isStepping(): Boolean = false
  override def stepInto(): Unit = ???
  override def stepOver(): Unit = ???
  override def stepReturn(): Unit = ???
  // Members declared in org.eclipse.debug.core.model.ISuspendResume
  override def canResume(): Boolean = false
  override def canSuspend(): Boolean = false
  override def isSuspended(): Boolean = false
  override def resume(): Unit = ???
  override def suspend(): Unit = ???
  // Members declared in org.eclipse.debug.core.model.ITerminate
  override def canTerminate(): Boolean = false
  override def isTerminated(): Boolean = false
  override def terminate(): Unit = ???

  object NullThread extends DebugElement(dbgTarget) with IThread {
    // Members declared in org.eclipse.debug.core.model.IDebugElement
    override def getModelIdentifier(): String = ScalaDebugPlugin.id
    // Members declared in org.eclipse.debug.core.model.IStep
    override def canStepInto(): Boolean = ???
    override def canStepOver(): Boolean = ???
    override def canStepReturn(): Boolean = ???
    override def isStepping(): Boolean = ???
    override def stepInto(): Unit = ???
    override def stepOver(): Unit = ???
    override def stepReturn(): Unit = ???
    // Members declared in org.eclipse.debug.core.model.ISuspendResume
    override def canResume(): Boolean = ???
    override def canSuspend(): Boolean = ???
    override def isSuspended(): Boolean = ???
    override def resume(): Unit = ???
    override def suspend(): Unit = ???
    // Members declared in org.eclipse.debug.core.model.ITerminate
    override def canTerminate(): Boolean = ???
    override def isTerminated(): Boolean = ???
    override def terminate(): Unit = ???
    // Members declared in org.eclipse.debug.core.model.IThread
    override def getBreakpoints(): Array[org.eclipse.debug.core.model.IBreakpoint] = ???
    override def getName(): String = "<dummy>"
    override def getPriority(): Int = ???
    override def getStackFrames(): Array[org.eclipse.debug.core.model.IStackFrame] = Array(getTopStackFrame)
    override def getTopStackFrame(): org.eclipse.debug.core.model.IStackFrame = AsyncStackFrame.this
    override def hasStackFrames(): Boolean = true
  }
}
case class AsyncLocalVariable(name: String, value: IValue, ordinal: Int)(val dbgTarget: IDebugTarget) extends DebugElement(dbgTarget) with IVariable {
  // Members declared in org.eclipse.debug.core.model.IDebugElement
  override def getModelIdentifier(): String = ScalaDebugPlugin.id
  // Members declared in org.eclipse.debug.core.model.IValueModification
  override def setValue(x$1: org.eclipse.debug.core.model.IValue): Unit = ???
  override def setValue(x$1: String): Unit = ???
  override def supportsValueModification(): Boolean = false
  override def verifyValue(x$1: org.eclipse.debug.core.model.IValue): Boolean = true
  override def verifyValue(x$1: String): Boolean = true

  // Members declared in org.eclipse.debug.core.model.IVariable
  override def getName(): String = name
  override def getReferenceTypeName(): String = "<unknown type>"
  override def getValue(): org.eclipse.debug.core.model.IValue = value
  override def hasValueChanged(): Boolean = false
}
