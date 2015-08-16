/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.model

import scala.collection.JavaConverters.asScalaBufferConverter
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.debug.core.DebugException
import org.eclipse.debug.core.model.IIndexedValue
import org.eclipse.debug.core.model.IValue
import org.eclipse.debug.core.model.IVariable
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.scalaide.util.Utils.jdiSynchronized
import com.sun.jdi.ArrayReference
import com.sun.jdi.BooleanValue
import com.sun.jdi.ByteValue
import com.sun.jdi.CharValue
import com.sun.jdi.ClassType
import com.sun.jdi.DoubleValue
import com.sun.jdi.Field
import com.sun.jdi.FloatValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.ShortValue
import com.sun.jdi.StringReference
import com.sun.jdi.Value
import com.sun.jdi.VoidValue

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

  /**
   * Mirroring 'normal' values into wrapped JDI ones.
   */
  def apply(value: Any, target: ScalaDebugTarget): ScalaValue = {
    value match {
      case s: String =>
        new ScalaStringReference(target.virtualMachine.mirrorOf(s), target)
      case int: Int =>
        val mirror = target.virtualMachine.mirrorOf(int)
        new ScalaPrimitiveValue("scala.Int", int.toString(), mirror, target)
      case _ =>
        ???
    }
  }

  final val BOXED_PRIMITIVE_TYPES = List("Ljava/lang/Integer;", "Ljava/lang/Long;", "Ljava/lang/Boolean;", "Ljava/lang/Byte;", "Ljava/lang/Double;", "Ljava/lang/Float;", "Ljava/lang/Short;")
  final val BOXED_CHAR_TYPE = "Ljava/lang/Character;"

}

abstract class ScalaValue(val underlying: Value, target: ScalaDebugTarget) extends ScalaDebugElement(target) with IValue {

  override def isAllocated(): Boolean = true

  final override def getReferenceTypeName(): String = jdiSynchronized {
    wrapJDIException("Exception while retrieving reference type name") { doGetReferenceTypeName() }
  }

  final override def getValueString(): String = jdiSynchronized {
    wrapJDIException("Exception while retrieving value string") { doGetValueString() }
  }

  final override def getVariables(): Array[IVariable] = jdiSynchronized {
    wrapJDIException("Exception while retrieving variables") { doGetVariables() }
  }

  final override def hasVariables(): Boolean = jdiSynchronized {
    wrapJDIException("Exception while checking if debug element has variables") { doHasVariables() }
  }

  /** Gets called by [[getReferenceTypeName]] to ensure that JDI exceptions are handled correctly. */
  protected def doGetReferenceTypeName(): String
  /** Gets called by [[getValueString]] to ensure that JDI exceptions are handled correctly. */
  protected def doGetValueString(): String
  /** Gets called by [[getVariables]] to ensure that JDI exceptions are handled correctly. */
  protected def doGetVariables(): Array[IVariable]
  /** Gets called by [[hasVariables]] to ensure that JDI exceptions are handled correctly. */
  protected def doHasVariables(): Boolean
}

class ScalaArrayReference(override val underlying: ArrayReference, target: ScalaDebugTarget) extends ScalaValue(underlying, target) with IIndexedValue {

  protected override def doGetReferenceTypeName(): String = "scala.Array"
  protected override def doGetValueString(): String = {
    val name = ScalaStackFrame.getSimpleName(underlying.referenceType.signature)
    s"$name($getSize) (id=${underlying.uniqueID})"
  }
  protected override def doGetVariables(): Array[IVariable] = getVariables(0, getSize)
  protected override def doHasVariables(): Boolean = getSize > 0

  override def getVariable(offset: Int): IVariable = new ScalaArrayElementVariable(offset, this)

  override def getVariables(offset: Int, length: Int): Array[IVariable] = (offset until offset + length).map(new ScalaArrayElementVariable(_, this)).toArray

  override def getSize(): Int = wrapJDIException("Exception while retrieving size") { underlying.length }

  override def getInitialOffset(): Int = 0

}

class ScalaPrimitiveValue(typeName: String, value: String, override val underlying: Value, target: ScalaDebugTarget) extends ScalaValue(underlying, target) {

  protected override def doGetReferenceTypeName(): String = typeName
  protected override def doGetValueString(): String = value
  protected override def doGetVariables(): Array[IVariable] = Array()
  protected override def doHasVariables(): Boolean = false

}

class ScalaStringReference(override val underlying: StringReference, target: ScalaDebugTarget) extends ScalaObjectReference(underlying, target) {

  protected override def doGetReferenceTypeName() = "java.lang.String"
  protected override def doGetValueString(): String = s""""${underlying.value}" (id=${underlying.uniqueID})"""

}

class ScalaObjectReference(override val underlying: ObjectReference, target: ScalaDebugTarget) extends ScalaValue(underlying, target) with HasFieldValue with HasMethodInvocation {
  import ScalaValue._

  protected override def doGetReferenceTypeName(): String = underlying.referenceType.name

  protected override def doGetValueString(): String = {
    val refTypeSignature = getReferenceType.signature
    val name = ScalaStackFrame.getSimpleName(refTypeSignature)

    if (BOXED_PRIMITIVE_TYPES.contains(refTypeSignature))
      s"$name $getBoxedPrimitiveValue (id=${underlying.uniqueID})"
    else if (refTypeSignature == BOXED_CHAR_TYPE)
      s"$name '$getBoxedPrimitiveValue' (id=${underlying.uniqueID})"
    else
      s"$name (id=${underlying.uniqueID})"
  }

  private def isVisible(field: ScalaVariable): Boolean =
    !field.isStatic

  protected override def doGetVariables(): Array[IVariable] = {
    import scala.collection.JavaConverters._
    referenceType.allFields.asScala.map(new ScalaFieldVariable(_, this)).filter(isVisible).sortBy(_.getName).toArray
  }
  protected override def doHasVariables(): Boolean = !referenceType.allFields.isEmpty

  protected override def getReferenceType: ReferenceType = underlying.referenceType()

  protected override def getJdiFieldValue(field: Field): Value = underlying.getValue(field)

  protected[model] override def classType: ClassType = referenceType.asInstanceOf[ClassType]

  override protected[model] def jdiInvokeMethod(method: Method, thread: ScalaThread, args: Value*): Value = {
    if (thread == null) {
      logger.debug(s"Cannot invoke method $method on $this because no thread is selected or debugged thread is no longer available")
      val status = new Status(IStatus.ERROR, ScalaDebugPlugin.id, "No thread is selected or debugged thread is no longer available")
      throw new DebugException(status)
    }

    thread.invokeMethod(underlying, method, args: _*)
  }

  /**
   * Return the string representation of the boxed primitive value.
   * Should be called only when this is a boxing instance.
   */
  private def getBoxedPrimitiveValue(): String = {
    ScalaDebugModelPresentation.computeDetail(fieldValue("value"))
  }
}

class ScalaNullValue(target: ScalaDebugTarget) extends ScalaValue(null, target) {

  protected override def doGetReferenceTypeName(): String = "null"
  protected override def doGetValueString(): String = "null"
  protected override def doGetVariables(): Array[IVariable] = Array()
  protected override def doHasVariables(): Boolean = false

}
