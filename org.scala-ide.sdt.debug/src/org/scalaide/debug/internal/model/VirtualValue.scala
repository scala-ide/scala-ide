package org.scalaide.debug.internal.model

import org.eclipse.debug.core.model.IValue
import org.scalaide.debug.internal.ScalaDebugger
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.eclipse.debug.core.model.IVariable

case class VirtualValue(refTypeName: String, valueString: String, fields: Seq[IVariable] = Seq.empty)(implicit debugTarget: ScalaDebugTarget)
  extends ScalaDebugElement(debugTarget) with IValue {

  def isAllocated(): Boolean = false

  def getReferenceTypeName(): String = refTypeName

  def getValueString(): String = valueString

  def withFields(vars: IVariable*) = {
    VirtualValue(refTypeName, valueString, fields ++ vars.toSeq)
  }

  def getVariables(): Array[IVariable] = fields.toArray

  def hasVariables(): Boolean = fields.isEmpty
}

case class VirtualVariable(name: String, refTypeName: String, value: IValue)(implicit debugTarget: ScalaDebugTarget) extends ScalaDebugElement(debugTarget) with IVariable {
  def getName(): String = name

  def getReferenceTypeName(): String = refTypeName

  def getValue(): IValue = value

  def hasValueChanged(): Boolean = false

  def supportsValueModification(): Boolean = false
  def setValue(x$1: IValue): Unit = ???
  def setValue(x$1: String): Unit = ???
  def verifyValue(x$1: IValue): Boolean = false
  def verifyValue(x$1: String): Boolean = false
}
