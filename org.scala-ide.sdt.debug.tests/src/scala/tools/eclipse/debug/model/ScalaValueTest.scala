package scala.tools.eclipse.debug.model

import org.junit.Test
import org.mockito.Mockito._
import org.junit.Assert._
import org.junit.Before
import org.eclipse.debug.core.DebugPlugin
import com.sun.jdi.BooleanValue
import com.sun.jdi.ByteValue
import com.sun.jdi.CharValue
import com.sun.jdi.DoubleValue
import com.sun.jdi.FloatValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.ShortValue
import com.sun.jdi.StringReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Field
import java.util.{ List => JList }
import com.sun.jdi.ObjectReference
import org.eclipse.jdt.internal.debug.core.model.JDIReferenceType
import com.sun.jdi.ArrayReference
import com.sun.jdi.ArrayType
import com.sun.jdi.ClassType
import org.eclipse.debug.core.model.IIndexedValue

object ScalaValueTest {
  def createFields(size: Int): JList[Field] = {
    import scala.collection.JavaConverters._

    createFieldsRec(size).asJava
  }

  def createFieldsRec(size: Int): List[Field] = {
    size match {
      case 0 =>
        Nil
      case _ =>
        val field = mock(classOf[Field])
        when(field.name).thenReturn("field" + size)
        createFieldsRec(size - 1) :+ field
    }
  }
}

class ScalaValueTest {
  import ScalaValueTest._

  @Before
  def initializeDebugPlugin() {
    if (DebugPlugin.getDefault == null) {
      new DebugPlugin
    }
  }

  @Test
  def booleanValueTrue() {
    val jdiValue = mock(classOf[BooleanValue])
    when(jdiValue.value).thenReturn(true)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "scala.Boolean", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "true", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }

  @Test
  def booleanValueFalse() {
    val jdiValue = mock(classOf[BooleanValue])
    when(jdiValue.value).thenReturn(false)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "scala.Boolean", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "false", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }

  @Test
  def byteValue() {
    val jdiValue = mock(classOf[ByteValue])
    when(jdiValue.value).thenReturn(64.toByte)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "scala.Byte", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "64", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }

  @Test
  def charValue() {
    val jdiValue = mock(classOf[CharValue])
    when(jdiValue.value).thenReturn('z')

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "scala.Char", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "z", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }

  @Test
  def doubleValue() {
    val jdiValue = mock(classOf[DoubleValue])
    when(jdiValue.value).thenReturn(4.55d)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "scala.Double", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "4.55", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }

  @Test
  def floatValue() {
    val jdiValue = mock(classOf[FloatValue])
    when(jdiValue.value).thenReturn(82.9f)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "scala.Float", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "82.9", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }

  @Test
  def intValue() {
    val jdiValue = mock(classOf[IntegerValue])
    when(jdiValue.value).thenReturn(32)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "scala.Int", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "32", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }

  @Test
  def longValue() {
    val jdiValue = mock(classOf[LongValue])
    when(jdiValue.value).thenReturn(128L)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "scala.Long", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "128", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }

  @Test
  def shortValue() {
    val jdiValue = mock(classOf[ShortValue])
    when(jdiValue.value).thenReturn(334.toShort)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "scala.Short", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "334", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }

  @Test
  def stringValue() {
    val jdiValue = mock(classOf[StringReference])
    when(jdiValue.value).thenReturn("some string")
    when(jdiValue.uniqueID).thenReturn(15)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    val fields = createFields(4)
    when(jdiReferenceType.allFields).thenReturn(fields)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "java.lang.String", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "\"some string\" (id=15)", scalaValue.getValueString)
    assertTrue("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 4, scalaValue.getVariables.length)
  }

  @Test
  def objectReferenceValue() {
    val jdiValue = mock(classOf[ObjectReference])
    when(jdiValue.uniqueID).thenReturn(22)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.name).thenReturn("some.package.TestClass")
    when(jdiReferenceType.signature).thenReturn("Lsome/package/TestClass;")
    val fields = createFields(8)
    when(jdiReferenceType.allFields).thenReturn(fields)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "some.package.TestClass", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "TestClass (id=22)", scalaValue.getValueString)
    assertTrue("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 8, scalaValue.getVariables.length)
  }

  @Test
  def objectReferenceValueNoFields() {
    val jdiValue = mock(classOf[ObjectReference])
    when(jdiValue.uniqueID).thenReturn(1223)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.name).thenReturn("some.other.package.OtherTestClass")
    when(jdiReferenceType.signature).thenReturn("Lsome/other/package/OtherTestClass;")
    val fields = createFields(0)
    when(jdiReferenceType.allFields).thenReturn(fields)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "some.other.package.OtherTestClass", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "OtherTestClass (id=1223)", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }

  @Test
  def objectReferenceValueWithEncodedName() {
    val jdiValue = mock(classOf[ObjectReference])
    when(jdiValue.uniqueID).thenReturn(666)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.name).thenReturn("scala.collection.immutable.$colon$colon")
    when(jdiReferenceType.signature).thenReturn("Lscala/collection/immutable/$colon$colon;")

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "scala.collection.immutable.$colon$colon", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", ":: (id=666)", scalaValue.getValueString)
  }

  @Test
  def objectReferenceBoxedInteger() {
    val jdiValue = mock(classOf[ObjectReference])
    when(jdiValue.uniqueID).thenReturn(2)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.signature).thenReturn("Ljava/lang/Integer;")
    val jdiField = mock(classOf[Field])
    when(jdiReferenceType.fieldByName("value")).thenReturn(jdiField)
    val jdiPrimitiveValue = mock(classOf[IntegerValue])
    when(jdiValue.getValue(jdiField)).thenReturn(jdiPrimitiveValue)
    when(jdiPrimitiveValue.value).thenReturn(4)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad value", "Integer 4 (id=2)", scalaValue.getValueString)
  }

  @Test
  def objectReferenceBoxedLong() {
    val jdiValue = mock(classOf[ObjectReference])
    when(jdiValue.uniqueID).thenReturn(4)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.signature).thenReturn("Ljava/lang/Long;")
    val jdiField = mock(classOf[Field])
    when(jdiReferenceType.fieldByName("value")).thenReturn(jdiField)
    val jdiPrimitiveValue = mock(classOf[LongValue])
    when(jdiValue.getValue(jdiField)).thenReturn(jdiPrimitiveValue)
    when(jdiPrimitiveValue.value).thenReturn(8)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad value", "Long 8 (id=4)", scalaValue.getValueString)
  }

  @Test
  def objectReferenceBoxedBoolean() {
    val jdiValue = mock(classOf[ObjectReference])
    when(jdiValue.uniqueID).thenReturn(8)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.signature).thenReturn("Ljava/lang/Boolean;")
    val jdiField = mock(classOf[Field])
    when(jdiReferenceType.fieldByName("value")).thenReturn(jdiField)
    val jdiPrimitiveValue = mock(classOf[BooleanValue])
    when(jdiValue.getValue(jdiField)).thenReturn(jdiPrimitiveValue)
    when(jdiPrimitiveValue.value).thenReturn(true)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad value", "Boolean true (id=8)", scalaValue.getValueString)
  }

  @Test
  def objectReferenceBoxedByte() {
    val jdiValue = mock(classOf[ObjectReference])
    when(jdiValue.uniqueID).thenReturn(16)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.signature).thenReturn("Ljava/lang/Byte;")
    val jdiField = mock(classOf[Field])
    when(jdiReferenceType.fieldByName("value")).thenReturn(jdiField)
    val jdiPrimitiveValue = mock(classOf[ByteValue])
    when(jdiValue.getValue(jdiField)).thenReturn(jdiPrimitiveValue)
    when(jdiPrimitiveValue.value).thenReturn(32.toByte)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad value", "Byte 32 (id=16)", scalaValue.getValueString)
  }

  @Test
  def objectReferenceBoxedChar() {
    val jdiValue = mock(classOf[ObjectReference])
    when(jdiValue.uniqueID).thenReturn(256)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.signature).thenReturn("Ljava/lang/Character;")
    val jdiField = mock(classOf[Field])
    when(jdiReferenceType.fieldByName("value")).thenReturn(jdiField)
    val jdiPrimitiveValue = mock(classOf[CharValue])
    when(jdiValue.getValue(jdiField)).thenReturn(jdiPrimitiveValue)
    when(jdiPrimitiveValue.value).thenReturn('t')

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad value", "Character 't' (id=256)", scalaValue.getValueString)
  }

  @Test
  def objectReferenceBoxedDouble() {
    val jdiValue = mock(classOf[ObjectReference])
    when(jdiValue.uniqueID).thenReturn(32)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.signature).thenReturn("Ljava/lang/Double;")
    val jdiField = mock(classOf[Field])
    when(jdiReferenceType.fieldByName("value")).thenReturn(jdiField)
    val jdiPrimitiveValue = mock(classOf[DoubleValue])
    when(jdiValue.getValue(jdiField)).thenReturn(jdiPrimitiveValue)
    when(jdiPrimitiveValue.value).thenReturn(0.64)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad value", "Double 0.64 (id=32)", scalaValue.getValueString)
  }

  @Test
  def objectReferenceBoxedFloat() {
    val jdiValue = mock(classOf[ObjectReference])
    when(jdiValue.uniqueID).thenReturn(64)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.signature).thenReturn("Ljava/lang/Float;")
    val jdiField = mock(classOf[Field])
    when(jdiReferenceType.fieldByName("value")).thenReturn(jdiField)
    val jdiPrimitiveValue = mock(classOf[FloatValue])
    when(jdiValue.getValue(jdiField)).thenReturn(jdiPrimitiveValue)
    when(jdiPrimitiveValue.value).thenReturn(1.28f)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad value", "Float 1.28 (id=64)", scalaValue.getValueString)
  }

  @Test
  def objectReferenceBoxedShort() {
    val jdiValue = mock(classOf[ObjectReference])
    when(jdiValue.uniqueID).thenReturn(128)
    val jdiReferenceType = mock(classOf[ClassType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.signature).thenReturn("Ljava/lang/Short;")
    val jdiField = mock(classOf[Field])
    when(jdiReferenceType.fieldByName("value")).thenReturn(jdiField)
    val jdiPrimitiveValue = mock(classOf[ShortValue])
    when(jdiValue.getValue(jdiField)).thenReturn(jdiPrimitiveValue)
    when(jdiPrimitiveValue.value).thenReturn(256.toShort)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad value", "Short 256 (id=128)", scalaValue.getValueString)
  }

  @Test
  def arrayReferenceValue() {
    val jdiValue = mock(classOf[ArrayReference])
    when(jdiValue.length).thenReturn(3)
    when(jdiValue.uniqueID).thenReturn(65)
    val jdiReferenceType = mock(classOf[ArrayType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.signature).thenReturn("[Lelement/package/ElementClass;")

    val scalaValue = ScalaValue(jdiValue, null).asInstanceOf[IIndexedValue]

    assertEquals("Bad type", "scala.Array", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "Array[ElementClass](3) (id=65)", scalaValue.getValueString)
    assertTrue("Should have variables", scalaValue.hasVariables)
    assertEquals("Should have 3 variables", 3, scalaValue.getVariables().length)

    // methods from IIndexedValue

    assertEquals("Should start at 0", 0, scalaValue.getInitialOffset)
    assertEquals("Should have 3 variables", 3, scalaValue.getSize)
    assertEquals("Wrong element 0", "(0)", scalaValue.getVariable(0).getName)
    val elements1to2= scalaValue.getVariables(1, 2)
    assertEquals("Wrong size for sublist", 2, elements1to2.length)
    assertEquals("Wrong element 1", "(1)", elements1to2(0).getName)
    assertEquals("Wrong element 2", "(2)", elements1to2(1).getName)
  }

  @Test
  def arrayReferenceValueZeroLength() {
    val jdiValue = mock(classOf[ArrayReference])
    when(jdiValue.length).thenReturn(0)
    when(jdiValue.uniqueID).thenReturn(92)
    val jdiReferenceType = mock(classOf[ArrayType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    when(jdiReferenceType.signature).thenReturn("[LAClass;")

    val scalaValue = ScalaValue(jdiValue, null).asInstanceOf[IIndexedValue]

    assertEquals("Bad type", "scala.Array", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "Array[AClass](0) (id=92)", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables().length)

    // methods from IIndexedValue

    assertEquals("Should start at 0", 0, scalaValue.getInitialOffset)
    assertEquals("Should have 3 variables", 0, scalaValue.getSize)
    // not point of testing getVariable(Int) and getVariables(Int, Int)
  }
}