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
        createFieldsRec(size - 1) :+ mock(classOf[Field])
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
    val fields = createFields(0)
    when(jdiReferenceType.allFields).thenReturn(fields)

    val scalaValue = ScalaValue(jdiValue, null)

    assertEquals("Bad type", "some.other.package.OtherTestClass", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "OtherTestClass (id=1223)", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }
  
  @Test
  def arrayReferenecValue() {
    val jdiValue = mock(classOf[ArrayReference])
    when(jdiValue.length).thenReturn(3)
    when(jdiValue.uniqueID).thenReturn(65)
    val jdiReferenceType = mock(classOf[ArrayType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    val jdiReferenceType2 = mock(classOf[ClassType])
    when(jdiReferenceType.componentType).thenReturn(jdiReferenceType2)
    when(jdiReferenceType2.name).thenReturn("element.package.ElementClass")
    
    val scalaValue = ScalaValue(jdiValue, null)
    
    assertEquals("Bad type", "scala.Array", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "Array[ElementClass](3) (id=65)", scalaValue.getValueString)
    assertTrue("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 3, scalaValue.getVariables.length)
  }

  @Test
  def arrayReferenecValueZeroLength() {
    val jdiValue = mock(classOf[ArrayReference])
    when(jdiValue.length).thenReturn(0)
    when(jdiValue.uniqueID).thenReturn(92)
    val jdiReferenceType = mock(classOf[ArrayType])
    when(jdiValue.referenceType).thenReturn(jdiReferenceType)
    val jdiReferenceType2 = mock(classOf[ClassType])
    when(jdiReferenceType.componentType).thenReturn(jdiReferenceType2)
    when(jdiReferenceType2.name).thenReturn("AClass")
    
    val scalaValue = ScalaValue(jdiValue, null)
    
    assertEquals("Bad type", "scala.Array", scalaValue.getReferenceTypeName)
    assertEquals("Bad value", "Array[AClass](0) (id=92)", scalaValue.getValueString)
    assertFalse("Should not have variables", scalaValue.hasVariables)
    assertEquals("Should not have variables", 0, scalaValue.getVariables.length)
  }}