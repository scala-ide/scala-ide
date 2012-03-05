package scala.tools.eclipse.debug.model

import org.eclipse.debug.core.model.IVariable

import com.sun.jdi.{LocalVariable, Field, ArrayType}

abstract class ScalaVariable(target: ScalaDebugTarget) extends ScalaDebugElement(target) with IVariable {

  // Members declared in org.eclipse.debug.core.model.IValueModification

  def setValue(x$1: org.eclipse.debug.core.model.IValue): Unit = ???
  def setValue(x$1: String): Unit = ???
  def supportsValueModification(): Boolean = false // TODO: need real logic
  def verifyValue(x$1: org.eclipse.debug.core.model.IValue): Boolean = ???
  def verifyValue(x$1: String): Boolean = ???

  // Members declared in org.eclipse.debug.core.model.IVariable

  def hasValueChanged(): Boolean = false // TODO: need real logic
}

class ScalaLocalVariable(variable: LocalVariable, stackFrame: ScalaStackFrame) extends ScalaVariable(stackFrame.getScalaDebugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  def getName(): String = variable.name
  def getReferenceTypeName(): String = variable.typeName
  def getValue(): org.eclipse.debug.core.model.IValue = ScalaValue(stackFrame.stackFrame.getValue(variable), getScalaDebugTarget)
}

class ScalaArrayVariable(index: Int, arrayReference: ScalaArrayReference) extends ScalaVariable(arrayReference.getScalaDebugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  def getName(): String = index.toString
  def getReferenceTypeName(): String = arrayReference.arrayReference.referenceType.asInstanceOf[ArrayType].componentTypeName
  def getValue(): org.eclipse.debug.core.model.IValue = ScalaValue(arrayReference.arrayReference.getValue(index), getScalaDebugTarget)

}

class ScalaFieldVariable(field: Field, objectReference: ScalaObjectReference) extends ScalaVariable(objectReference.getScalaDebugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  def getName(): String = field.name
  def getReferenceTypeName(): String = field.typeName
  def getValue(): org.eclipse.debug.core.model.IValue = ScalaValue(objectReference.objectReference.getValue(field), getScalaDebugTarget)
}