package scala.tools.eclipse.debug.model

import scala.collection.JavaConverters.asScalaBufferConverter
import org.eclipse.debug.core.model.IIndexedValue
import org.eclipse.debug.core.model.IValue
import org.eclipse.debug.core.model.IVariable
import com.sun.jdi.{ VoidValue, Value, StringReference, ShortValue, ObjectReference, LongValue, IntegerValue, FloatValue, DoubleValue, CharValue, ByteValue, BooleanValue, ArrayReference }
import com.sun.jdi.ClassType
import com.sun.jdi.PrimitiveValue
import com.sun.jdi.Field
import com.sun.jdi.Method
import scala.tools.eclipse.debug.JDIUtil
import com.sun.jdi.ReferenceType

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

abstract class ScalaValue(val underlying: Value, target: ScalaDebugTarget) extends ScalaDebugElement(target) with IValue {

  // Members declared in org.eclipse.debug.core.model.IValue

  override def isAllocated(): Boolean = true // TODO: should always be true with a JVM, to check. ObjectReference#isCollected ?

  final override def getReferenceTypeName(): String =
    wrapJDIException("Exception while retrieving reference type name") { doGetReferenceTypeName() }

  final override def getValueString(): String =
    wrapJDIException("Exception while retrieving value string") { doGetValueString() }

  final override def getVariables(): Array[IVariable] =
    wrapJDIException("Exception while retrieving variables") { doGetVariables() }
  
  final override def hasVariables(): Boolean = 
    wrapJDIException("Exception while checking if debug element has variables") { doHasVariables() }

  protected def doGetReferenceTypeName(): String
  protected def doGetValueString(): String
  protected def doGetVariables(): Array[IVariable]
  protected def doHasVariables(): Boolean
}

class ScalaArrayReference(override val underlying: ArrayReference, target: ScalaDebugTarget) extends ScalaValue(underlying, target) with IIndexedValue {

  // Members declared in org.eclipse.debug.core.model.IValue

  protected override def doGetReferenceTypeName(): String = "scala.Array"
  protected override def doGetValueString(): String = "%s(%d) (id=%d)".format(ScalaStackFrame.getSimpleName(underlying.referenceType.signature), getSize, underlying.uniqueID)
  protected override def doGetVariables(): Array[IVariable] = getVariables(0, getSize)
  protected override def doHasVariables(): Boolean = getSize > 0 
  
  // Members declared in org.eclipse.debug.core.model.IIndexedValue
  
  override def getVariable(offset: Int) : IVariable = new ScalaArrayElementVariable(offset, this)
  
  override def getVariables(offset: Int, length: Int) : Array[IVariable] = (offset until offset + length).map(new ScalaArrayElementVariable(_, this)).toArray
  
  override def getSize(): Int = 
    wrapJDIException("Exception while retrieving size") { underlying.length }

  override def getInitialOffset(): Int = 0

}

class ScalaPrimitiveValue(typeName: String, value: String, override val underlying: Value, target: ScalaDebugTarget) extends ScalaValue(underlying, target) {

  // Members declared in org.eclipse.debug.core.model.IValue

  protected override def doGetReferenceTypeName(): String = typeName
  protected override def doGetValueString(): String = value
  protected override def doGetVariables(): Array[IVariable] = Array()
  protected override def doHasVariables(): Boolean = false

}

class ScalaStringReference(override val underlying: StringReference, target: ScalaDebugTarget) extends ScalaObjectReference(underlying, target) {

  protected override def doGetReferenceTypeName() = "java.lang.String"
  protected override def doGetValueString(): String = """"%s" (id=%d)""".format(underlying.value, underlying.uniqueID)

}

class ScalaObjectReference(override val underlying: ObjectReference, target: ScalaDebugTarget) extends ScalaValue(underlying, target) with HasFieldValue with HasMethodInvocation {
  import ScalaValue._

  // Members declared in org.eclipse.debug.core.model.IValue

  protected override def doGetReferenceTypeName(): String = underlying.referenceType.name

  protected override def doGetValueString(): String = {
    // TODO: move to string builder?
    if (BOXED_PRIMITIVE_TYPES.contains(referenceType.signature)) {
      "%s %s (id=%d)".format(ScalaStackFrame.getSimpleName(referenceType.signature), getBoxedPrimitiveValue(), underlying.uniqueID)
    } else if (BOXED_CHAR_TYPE == referenceType.signature) {
      "%s '%s' (id=%d)".format(ScalaStackFrame.getSimpleName(referenceType.signature), getBoxedPrimitiveValue(), underlying.uniqueID)
    } else {
      "%s (id=%d)".format(ScalaStackFrame.getSimpleName(referenceType.signature), underlying.uniqueID)
    }
  }

  protected override def doGetVariables(): Array[IVariable] = {
    import scala.collection.JavaConverters._
    referenceType.allFields.asScala.map(new ScalaFieldVariable(_, this)).sortBy(_.getName).toArray
  }
  protected override def doHasVariables(): Boolean = !referenceType.allFields.isEmpty

  // Members declared in scala.tools.eclipse.debug.model.HasFieldValue
  
  protected override def getReferenceType: ReferenceType = underlying.referenceType()
  
  protected override def getJdiFieldValue(field: Field): Value = underlying.getValue(field)
  
  // Members declared in scala.tools.eclipse.debug.model.HasMethodInvocation
  
  protected[model] override def classType: ClassType = referenceType.asInstanceOf[ClassType]
  
  protected[model] def jdiInvokeMethod(method: Method, thread: ScalaThread, args: Value*): Value = thread.invokeMethod(underlying, method, args:_*)

  // -----

  /** Return the string representation of the boxed primitive value.
   *  Should be called only when this is a boxing instance.
   */
  private def getBoxedPrimitiveValue(): String = wrapJDIException("Exception while retrieving boxed primitive value") {
    ScalaDebugModelPresentation.computeDetail(fieldValue("value"))
  } 
}

class ScalaNullValue(target: ScalaDebugTarget) extends ScalaValue(null, target) {

  // Members declared in org.eclipse.debug.core.model.IValue

  protected override def doGetReferenceTypeName(): String = "null"
  protected override def doGetValueString(): String = "null"
  protected override def doGetVariables(): Array[IVariable] = Array() // TODO: cached empty array?
  protected override def doHasVariables(): Boolean = false

}