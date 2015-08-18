package org.scalaide.debug.internal.model

import org.eclipse.debug.core.model.IValue
import org.scalaide.debug.internal.ScalaDebugger
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.eclipse.debug.core.model.IVariable

case class VirtualValue
    (refTypeName: String, valueString: String, fields: Seq[IVariable] = Seq.empty)
    (implicit debugTarget: ScalaDebugTarget)
      extends ScalaDebugElement(debugTarget)
      with IValue {

  override def isAllocated(): Boolean = false

  override def getReferenceTypeName(): String = refTypeName

  override def getValueString(): String = valueString

  def withFields(vars: IVariable*) =
    VirtualValue(refTypeName, valueString, fields ++ vars.toSeq)

  override def getVariables(): Array[IVariable] = fields.toArray

  override def hasVariables(): Boolean = fields.nonEmpty
}

case class VirtualVariable
    (name: String, refTypeName: String, value: IValue)
    (implicit debugTarget: ScalaDebugTarget)
      extends ScalaDebugElement(debugTarget)
      with IVariable {

  override def getName(): String = name

  override def getReferenceTypeName(): String = refTypeName

  override def getValue(): IValue = value

  override def hasValueChanged(): Boolean = false

  override def supportsValueModification(): Boolean = false
  override def setValue(value: IValue): Unit = ???
  override def setValue(expr: String): Unit = ???
  override def verifyValue(value: IValue): Boolean = false
  override def verifyValue(expr: String): Boolean = false
}
