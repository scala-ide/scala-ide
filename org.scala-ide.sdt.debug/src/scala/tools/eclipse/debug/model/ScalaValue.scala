package scala.tools.eclipse.debug.model

import scala.collection.JavaConverters.asScalaBufferConverter

import org.eclipse.debug.core.model.IValue

import com.sun.jdi.{VoidValue, Value, StringReference, ShortValue, ObjectReference, LongValue, IntegerValue, FloatValue, DoubleValue, CharValue, ByteValue, BooleanValue, ArrayReference}

object ScalaValue {

  def apply(value: Value, target: ScalaDebugTarget): ScalaValue = {
    value match {
      case arrayReference: ArrayReference =>
        new ScalaArrayReference(arrayReference, target)
      case booleanValue: BooleanValue =>
        // TODO: cache the values?
        new ScalaPrimitiveValue("boolean", booleanValue.value.toString, target)
      case byteValue: ByteValue =>
        new ScalaPrimitiveValue("byte", byteValue.value.toString, target)
      case charValue: CharValue =>
        new ScalaPrimitiveValue("char", charValue.value.toString, target)
      case doubleValue: DoubleValue =>
        new ScalaPrimitiveValue("double", doubleValue.value.toString, target)
      case floatValue: FloatValue =>
        new ScalaPrimitiveValue("fload", floatValue.value.toString, target)
      case integerValue: IntegerValue =>
        new ScalaPrimitiveValue("integer", integerValue.value.toString, target)
      case longValue: LongValue =>
        new ScalaPrimitiveValue("long", longValue.value.toString, target)
      case shortValue: ShortValue =>
        new ScalaPrimitiveValue("short", shortValue.value.toString, target)
      case stringReference: StringReference =>
        new ScalaPrimitiveValue("java.lang.String", stringReference.value, target)
      case objectReference: ObjectReference => // include ClassLoaderReference, ClassObjectReference, ThreadGroupReference, ThreadReference
        new ScalaObjectReference(objectReference, target)
      case null =>
        // TODO : cache one per target
        new ScalaNullValue(target)
      case voidValue: VoidValue =>
        ??? // TODO: in what cases do we get this value ?
      case _ =>
        ???
    }
  }

}

abstract class ScalaValue(target: ScalaDebugTarget) extends ScalaDebugElement(target) with IValue {

  // Members declared in org.eclipse.debug.core.model.IValue

  def isAllocated(): Boolean = true // TODO: should always be true with a JVM, to check. ObjectReference#isCollected ?

}

class ScalaArrayReference(val arrayReference: ArrayReference, target: ScalaDebugTarget) extends ScalaValue(target) {

  // Members declared in org.eclipse.debug.core.model.IValue

  def getReferenceTypeName(): String = arrayReference.`type`.name
  def getValueString(): String = "an array" // TODO: need real value
  def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = {
    (0 until arrayReference.length).map(new ScalaArrayVariable(_, this)).toArray
  }
  def hasVariables(): Boolean = arrayReference.length > 0
}

class ScalaPrimitiveValue(typeName: String, value: String, target: ScalaDebugTarget) extends ScalaValue(target) {

  def getReferenceTypeName(): String = typeName
  def getValueString(): String = value
  def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = Array()
  def hasVariables(): Boolean = false

}

class ScalaObjectReference(val objectReference: ObjectReference, target: ScalaDebugTarget) extends ScalaValue(target) {

  def getReferenceTypeName(): String = objectReference.referenceType.name
  def getValueString(): String = "an object" // TODO: need real value
  def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = {
    import scala.collection.JavaConverters._
    objectReference.referenceType.allFields.asScala.map(new ScalaFieldVariable(_, this)).toArray
  }
  def hasVariables(): Boolean = !objectReference.referenceType.allFields.isEmpty

}

class ScalaNullValue(target: ScalaDebugTarget) extends ScalaValue(target) {

  def getReferenceTypeName(): String = "null"
  def getValueString(): String = "null"
  def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = Array()
  def hasVariables(): Boolean = false

}