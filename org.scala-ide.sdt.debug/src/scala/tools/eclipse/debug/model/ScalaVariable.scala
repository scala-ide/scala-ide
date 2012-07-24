package scala.tools.eclipse.debug.model

import org.eclipse.debug.core.model.IVariable
import com.sun.jdi.{LocalVariable, Field, ArrayType}
import com.sun.jdi.ObjectReference
import org.eclipse.debug.core.model.IValue

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

class ScalaThisVariable(value: ObjectReference, stackFrame: ScalaStackFrame) extends ScalaVariable(stackFrame.debugTarget) {
  
  // Members declared in org.eclipse.debug.core.model.IVariable

  def getName(): String = "this"
  def getReferenceTypeName(): String = value.referenceType.name
  def getValue(): org.eclipse.debug.core.model.IValue = new ScalaObjectReference(value, debugTarget)
}

class ScalaLocalVariable(variable: LocalVariable, stackFrame: ScalaStackFrame) extends ScalaVariable(stackFrame.debugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  def getName(): String = variable.name
  def getReferenceTypeName(): String = variable.typeName
  
  // fetching the value for local variables cannot be delayed because the underlying stackframe element may become invalid at any time
  val getValue: IValue = ScalaValue(stackFrame.stackFrame.getValue(variable), debugTarget)
}

class ScalaArrayElementVariable(index: Int, arrayReference: ScalaArrayReference) extends ScalaVariable(arrayReference.debugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  def getName(): String = "(%s)".format(index)
  def getReferenceTypeName(): String = arrayReference.arrayReference.referenceType.asInstanceOf[ArrayType].componentTypeName
  def getValue(): org.eclipse.debug.core.model.IValue = ScalaValue(arrayReference.arrayReference.getValue(index), debugTarget)

}

class ScalaFieldVariable(field: Field, objectReference: ScalaObjectReference) extends ScalaVariable(objectReference.debugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  def getName(): String = field.name
  def getReferenceTypeName(): String = field.typeName
  def getValue(): org.eclipse.debug.core.model.IValue = ScalaValue(objectReference.objectReference.getValue(field), debugTarget)
}