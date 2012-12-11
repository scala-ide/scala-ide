package scala.tools.eclipse.debug.model

import org.eclipse.debug.core.model.IVariable
import com.sun.jdi.{LocalVariable, Field, ArrayType}
import com.sun.jdi.ObjectReference
import org.eclipse.debug.core.model.IValue

abstract class ScalaVariable(target: ScalaDebugTarget) extends ScalaDebugElement(target) with IVariable {

  // Members declared in org.eclipse.debug.core.model.IValueModification

  override def setValue(value: IValue): Unit = ???
  override def setValue(value: String): Unit = ???
  override def supportsValueModification: Boolean = false // TODO: need real logic
  override def verifyValue(value: IValue): Boolean = ???
  override def verifyValue(value: String): Boolean = ???

  // Members declared in org.eclipse.debug.core.model.IVariable

  override def hasValueChanged: Boolean = false // TODO: need real logic
}

class ScalaThisVariable(underlying: ObjectReference, stackFrame: ScalaStackFrame) extends ScalaVariable(stackFrame.getDebugTarget) {
  
  // Members declared in org.eclipse.debug.core.model.IVariable

  override def getName: String = "this"
  override def getReferenceTypeName: String = underlying.referenceType.name
  override def getValue: IValue = new ScalaObjectReference(underlying, getDebugTarget)
}

class ScalaLocalVariable(underlying: LocalVariable, stackFrame: ScalaStackFrame) extends ScalaVariable(stackFrame.getDebugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  override def getName(): String = underlying.name
  override def getReferenceTypeName(): String = underlying.typeName
  
  // fetching the value for local variables cannot be delayed because the underlying stackframe element may become invalid at any time
  override val getValue: IValue = ScalaValue(stackFrame.stackFrame.getValue(underlying), getDebugTarget)
}

class ScalaArrayElementVariable(index: Int, arrayReference: ScalaArrayReference) extends ScalaVariable(arrayReference. getDebugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  override def getName(): String = "(%s)".format(index)
  override def getReferenceTypeName(): String = arrayReference.underlying.referenceType.asInstanceOf[ArrayType].componentTypeName
  override def getValue(): org.eclipse.debug.core.model.IValue = ScalaValue(arrayReference.underlying.getValue(index), getDebugTarget)

}

class ScalaFieldVariable(field: Field, objectReference: ScalaObjectReference) extends ScalaVariable(objectReference.getDebugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  override def getName(): String = field.name
  override def getReferenceTypeName(): String = field.typeName
  override def getValue(): org.eclipse.debug.core.model.IValue = ScalaValue(objectReference.underlying.getValue(field), getDebugTarget)
}