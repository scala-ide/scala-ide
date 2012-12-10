package scala.tools.eclipse.debug.model

import scala.collection.JavaConverters.asScalaBufferConverter
import org.eclipse.debug.core.model.IIndexedValue
import org.eclipse.debug.core.model.IValue
import org.eclipse.debug.core.model.IVariable
import com.sun.jdi.{ VoidValue, Value, StringReference, ShortValue, ObjectReference, LongValue, IntegerValue, FloatValue, DoubleValue, CharValue, ByteValue, BooleanValue, ArrayReference }
import com.sun.jdi.ClassType
import com.sun.jdi.PrimitiveValue

object ScalaValue {

  /**
   * Returns the given JDI value wrapped 
   */
  def apply(value: Value, target: ScalaDebugTarget): ScalaValue = {
    value match {
      case arrayReference: ArrayReference =>
        new ScalaArrayReference(arrayReference, target)
      case booleanValue: BooleanValue =>
        // TODO: cache the values?
        new ScalaPrimitiveValue("scala.Boolean", booleanValue.value.toString, booleanValue, target)
      case byteValue: ByteValue =>
        new ScalaPrimitiveValue("scala.Byte", byteValue.value.toString, byteValue, target)
      case charValue: CharValue =>
        new ScalaPrimitiveValue("scala.Char", charValue.value.toString, charValue, target)
      case doubleValue: DoubleValue =>
        new ScalaPrimitiveValue("scala.Double", doubleValue.value.toString, doubleValue, target)
      case floatValue: FloatValue =>
        new ScalaPrimitiveValue("scala.Float", floatValue.value.toString, floatValue, target)
      case integerValue: IntegerValue =>
        new ScalaPrimitiveValue("scala.Int", integerValue.value.toString, integerValue, target)
      case longValue: LongValue =>
        new ScalaPrimitiveValue("scala.Long", longValue.value.toString, longValue, target)
      case shortValue: ShortValue =>
        new ScalaPrimitiveValue("scala.Short", shortValue.value.toString, shortValue, target)
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

  /** Mirroring 'normal' values into wrapped JDI ones
   */
  def apply(value: Any, target: ScalaDebugTarget): ScalaValue = {
    value match {
      case s: String =>
        new ScalaStringReference(target.virtualMachine.mirrorOf(s), target)
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

  override def isAllocated(): Boolean = true // TODO: should always be true with a JVM, to check. ObjectReference#isCollected ?

  // new Members

  /** Returns the JDI value wrapped inside this Scala debug model value.
   */
  def value: Value

}

class ScalaArrayReference(val arrayReference: ArrayReference, target: ScalaDebugTarget) extends ScalaValue(target) with IIndexedValue {

  // Members declared in org.eclipse.debug.core.model.IValue

  override def getReferenceTypeName(): String = "scala.Array"
  override def getValueString(): String = "%s(%d) (id=%d)".format(ScalaStackFrame.getSimpleName(arrayReference.referenceType.signature), arrayReference.length, arrayReference.uniqueID)
  override def getVariables(): Array[IVariable] = getVariables(0, arrayReference.length)
  override def hasVariables(): Boolean = arrayReference.length > 0
  
  // Members declared in org.eclipse.debug.core.model.IIndexedValue
  
  override def getVariable(offset: Int) : IVariable = new ScalaArrayElementVariable(offset, this)
  
  override def getVariables(offset: Int, length: Int) : Array[IVariable] = (offset until offset + length).map(new ScalaArrayElementVariable(_, this)).toArray
  
  override def getSize(): Int = arrayReference.length	

  override def getInitialOffset(): Int = 0

  // Members declared in scala.tools.eclipse.debug.model.ScalaValue

  override def value = arrayReference

}

class ScalaPrimitiveValue(typeName: String, value: String, jdiValue: Value, target: ScalaDebugTarget) extends ScalaValue(target) {

  // Members declared in org.eclipse.debug.core.model.IValue

  override def getReferenceTypeName(): String = typeName
  override def getValueString(): String = value
  override def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = Array()
  override def hasVariables(): Boolean = false

  // Members declared in scala.tools.eclipse.debug.model.ScalaValue

  override def value = jdiValue

}

class ScalaStringReference(val stringReference: StringReference, target: ScalaDebugTarget) extends ScalaObjectReference(stringReference, target) {

  override def getReferenceTypeName() = "java.lang.String"
  override def getValueString(): String = """"%s" (id=%d)""".format(stringReference.value, stringReference.uniqueID)

}

class ScalaObjectReference(val objectReference: ObjectReference, target: ScalaDebugTarget) extends ScalaValue(target) {
  import ScalaValue._

  // Members declared in org.eclipse.debug.core.model.IValue

  override def getReferenceTypeName(): String = objectReference.referenceType.name

  override def getValueString(): String = {
    // TODO: move to string builder?
    if (BOXED_PRIMITIVE_TYPES.contains(objectReference.referenceType.signature)) {
      "%s %s (id=%d)".format(ScalaStackFrame.getSimpleName(objectReference.referenceType.signature), getBoxedPrimitiveValue(), objectReference.uniqueID)
    } else if (BOXED_CHAR_TYPE == objectReference.referenceType.signature) {
      "%s '%s' (id=%d)".format(ScalaStackFrame.getSimpleName(objectReference.referenceType.signature), getBoxedPrimitiveValue(), objectReference.uniqueID)
    } else {
      "%s (id=%d)".format(ScalaStackFrame.getSimpleName(objectReference.referenceType.signature), objectReference.uniqueID)
    }
  }

  override def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = {
    import scala.collection.JavaConverters._
    objectReference.referenceType.allFields.asScala.map(new ScalaFieldVariable(_, this)).sortBy(_.getName).toArray
  }
  override def hasVariables(): Boolean = !objectReference.referenceType.allFields.isEmpty

  // Members declared in scala.tools.eclipse.debug.model.ScalaValue

  override def value = objectReference

  // -----

  /** Return the string representation of the boxed primitive value.
   *  Should be called only when this is a boxing instance.
   */
  private def getBoxedPrimitiveValue(): String = {
    ScalaDebugModelPresentation.computeDetail(fieldValue("value"))
  }
  
  /** Invoke the method with given name, using the given arguments.
   * 
   * @throws IllegalArgumentException if no method with given name exists, or more than one.
   */  
  def invokeMethod(methodName: String, thread: ScalaThread, args: ScalaValue*): ScalaValue = {
    val methods= objectReference.referenceType().methodsByName(methodName)
    methods.size match {
      case 0 =>
        throw new IllegalArgumentException("Method '%s(..)' doesn't exist for '%s'".format(methodName, objectReference.referenceType().name()))
      case 1 =>
        thread.invokeMethod(objectReference, methods.get(0), args:_*)
      case _ =>
        throw new IllegalArgumentException("More than on method '%s(..)' for '%s'".format(methodName, objectReference.referenceType().name()))
    }
  }
  
  /** Invoke the method with given name and signature, using the given arguments.
   * 
   * @throws IllegalArgumentException if no method with given name exists.
   */  
  def invokeMethod(methodName: String, methodSignature: String, thread: ScalaThread, args: ScalaValue*): ScalaValue = {
    val method= objectReference.referenceType().asInstanceOf[ClassType].concreteMethodByName(methodName, methodSignature)
    if (method == null) {
      throw new IllegalArgumentException("Method '%s%s' doesn't exist for '%s'".format(methodName, methodSignature, objectReference.referenceType().name()))
    }
    thread.invokeMethod(objectReference, method, args:_*)
  }
  
  /** Return the value of the field with the given name.
   * 
   * @throws IllegalArgumentException if the no field with the given name exists.
   */
  def fieldValue(fieldName: String): ScalaValue = {
    val field= objectReference.referenceType().fieldByName(fieldName)
    if (field == null) {
      throw new IllegalArgumentException("Field '%s' doesn't exist for '%s'".format(fieldName, objectReference.referenceType.name()))
    }
    ScalaValue(objectReference.getValue(field), target)
  }


}

class ScalaNullValue(target: ScalaDebugTarget) extends ScalaValue(target) {

  // Members declared in org.eclipse.debug.core.model.IValue

  override def getReferenceTypeName(): String = "null"
  override def getValueString(): String = "null"
  override def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = Array() // TODO: cached empty array?
  override def hasVariables(): Boolean = false

  override def value = null

}