package scala.tools.eclipse.debug.model

import scala.collection.JavaConverters.asScalaBufferConverter

import org.eclipse.debug.core.model.IIndexedValue
import org.eclipse.debug.core.model.IValue
import org.eclipse.debug.core.model.IVariable

import com.sun.jdi.{ VoidValue, Value, StringReference, ShortValue, ObjectReference, LongValue, IntegerValue, FloatValue, DoubleValue, CharValue, ByteValue, BooleanValue, ArrayReference }
import com.sun.jdi.ClassType
import com.sun.jdi.PrimitiveValue

object ScalaValue {

  def apply(value: Value, target: ScalaDebugTarget): ScalaValue = {
    value match {
      case arrayReference: ArrayReference =>
        new ScalaArrayReference(arrayReference, target)
      case booleanValue: BooleanValue =>
        // TODO: cache the values?
        new ScalaPrimitiveValue("scala.Boolean", booleanValue.value.toString, target)
      case byteValue: ByteValue =>
        new ScalaPrimitiveValue("scala.Byte", byteValue.value.toString, target)
      case charValue: CharValue =>
        new ScalaPrimitiveValue("scala.Char", charValue.value.toString, target)
      case doubleValue: DoubleValue =>
        new ScalaPrimitiveValue("scala.Double", doubleValue.value.toString, target)
      case floatValue: FloatValue =>
        new ScalaPrimitiveValue("scala.Float", floatValue.value.toString, target)
      case integerValue: IntegerValue =>
        new ScalaPrimitiveValue("scala.Int", integerValue.value.toString, target)
      case longValue: LongValue =>
        new ScalaPrimitiveValue("scala.Long", longValue.value.toString, target)
      case shortValue: ShortValue =>
        new ScalaPrimitiveValue("scala.Short", shortValue.value.toString, target)
      case stringReference: StringReference =>
        new ScalaStringReference(stringReference, target)
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

  final val BOXED_PRIMITIVE_TYPES = List("Ljava/lang/Integer;", "Ljava/lang/Long;", "Ljava/lang/Boolean;", "Ljava/lang/Byte;", "Ljava/lang/Double;", "Ljava/lang/Float;", "Ljava/lang/Short;")
  final val BOXED_CHAR_TYPE = "Ljava/lang/Character;"

}

// TODO: cache values?

abstract class ScalaValue(target: ScalaDebugTarget) extends ScalaDebugElement(target) with IValue {

  // Members declared in org.eclipse.debug.core.model.IValue

  def isAllocated(): Boolean = true // TODO: should always be true with a JVM, to check. ObjectReference#isCollected ?

}

class ScalaArrayReference(val arrayReference: ArrayReference, target: ScalaDebugTarget) extends ScalaValue(target) with IIndexedValue {

  // Members declared in org.eclipse.debug.core.model.IValue

  def getReferenceTypeName(): String = "scala.Array"
  def getValueString(): String = "%s(%d) (id=%d)".format(ScalaStackFrame.getSimpleName(arrayReference.referenceType.signature), arrayReference.length, arrayReference.uniqueID)
  def getVariables(): Array[IVariable] = getVariables(0, arrayReference.length)
  def hasVariables(): Boolean = arrayReference.length > 0
  
  // Members declared in org.eclipse.debug.core.model.IIndexedValue
  
  def getVariable(offset: Int) : IVariable = new ScalaArrayElementVariable(offset, this)
  
  def getVariables(offset: Int, length: Int) : Array[IVariable] = (offset until offset + length).map(new ScalaArrayElementVariable(_, this)).toArray
  
  def getSize(): Int = arrayReference.length	

  def getInitialOffset(): Int = 0
}

class ScalaPrimitiveValue(typeName: String, value: String, target: ScalaDebugTarget) extends ScalaValue(target) {

  // Members declared in org.eclipse.debug.core.model.IValue

  def getReferenceTypeName(): String = typeName
  def getValueString(): String = value
  def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = Array()
  def hasVariables(): Boolean = false

}

class ScalaStringReference(val stringReference: StringReference, target: ScalaDebugTarget) extends ScalaObjectReference(stringReference, target) {

  override def getReferenceTypeName() = "java.lang.String"
  override def getValueString(): String = """"%s" (id=%d)""".format(stringReference.value, stringReference.uniqueID)

}

class ScalaObjectReference(val objectReference: ObjectReference, target: ScalaDebugTarget) extends ScalaValue(target) {
  import ScalaValue._

  // Members declared in org.eclipse.debug.core.model.IValue

  def getReferenceTypeName(): String = objectReference.referenceType.name

  def getValueString(): String = {
    // TODO: move to string builder?
    if (BOXED_PRIMITIVE_TYPES.contains(objectReference.referenceType.signature)) {
      "%s %s (id=%d)".format(ScalaStackFrame.getSimpleName(objectReference.referenceType.signature), getBoxedPrimitiveValue(objectReference), objectReference.uniqueID)
    } else if (BOXED_CHAR_TYPE == objectReference.referenceType.signature) {
      "%s '%s' (id=%d)".format(ScalaStackFrame.getSimpleName(objectReference.referenceType.signature), getBoxedPrimitiveValue(objectReference), objectReference.uniqueID)
    } else {
      "%s (id=%d)".format(ScalaStackFrame.getSimpleName(objectReference.referenceType.signature), objectReference.uniqueID)
    }
  }

  def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = {
    import scala.collection.JavaConverters._
    objectReference.referenceType.allFields.asScala.map(new ScalaFieldVariable(_, this)).sortBy(_.getName).toArray
  }
  def hasVariables(): Boolean = !objectReference.referenceType.allFields.isEmpty

  // -----

  /**
   * Return the string representation of the contained primitive value.
   * Assumes the it is of boxed primitive type.
   */
  def getBoxedPrimitiveValue(objectReference: ObjectReference): String = {
    // TODO: cache field per target?
    // could use toString instead, but a method invocation is heavier
    val field = objectReference.referenceType.asInstanceOf[ClassType].fieldByName("value")
    val value = objectReference.getValue(field).asInstanceOf[PrimitiveValue]
    ScalaDebugModelPresentation.computeDetail(value)
  }

}

class ScalaNullValue(target: ScalaDebugTarget) extends ScalaValue(target) {

  // Members declared in org.eclipse.debug.core.model.IValue

  def getReferenceTypeName(): String = "null"
  def getValueString(): String = "null"
  def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = Array() // TODO: cached empty array?
  def hasVariables(): Boolean = false

}