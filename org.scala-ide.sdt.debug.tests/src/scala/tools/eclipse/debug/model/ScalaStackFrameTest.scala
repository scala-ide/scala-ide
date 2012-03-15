package scala.tools.eclipse.debug.model

import org.junit.Before
import org.eclipse.debug.core.DebugPlugin
import org.junit.Test
import org.junit.Assert._
import org.mockito.Mockito._
import com.sun.jdi.StackFrame
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.BooleanType
import java.util.{ List => JList }
import com.sun.jdi.Type
import org.eclipse.jdt.internal.debug.core.model.JDIReferenceType
import com.sun.jdi.ByteType
import com.sun.jdi.CharType
import com.sun.jdi.DoubleType
import com.sun.jdi.FloatType
import com.sun.jdi.IntegerType
import com.sun.jdi.LongType
import com.sun.jdi.ShortType
import com.sun.jdi.ArrayType
import com.sun.jdi.LocalVariable

object ScalaStackFrameTest {

  lazy val booleanType: BooleanType = {
    val jdiReferenceType = mock(classOf[BooleanType])
    when(jdiReferenceType.name).thenReturn("Boolean")
    jdiReferenceType
  }

  lazy val byteType: ByteType = {
    val jdiReferenceType = mock(classOf[ByteType])
    when(jdiReferenceType.name).thenReturn("Byte")
    jdiReferenceType
  }

  lazy val charType: CharType = {
    val jdiReferenceType = mock(classOf[CharType])
    when(jdiReferenceType.name).thenReturn("Char")
    jdiReferenceType
  }

  lazy val doubleType: DoubleType = {
    val jdiReferenceType = mock(classOf[DoubleType])
    when(jdiReferenceType.name).thenReturn("Double")
    jdiReferenceType
  }

  lazy val floatType: FloatType = {
    val jdiReferenceType = mock(classOf[FloatType])
    when(jdiReferenceType.name).thenReturn("Float")
    jdiReferenceType
  }

  lazy val intType: IntegerType = {
    val jdiReferenceType = mock(classOf[IntegerType])
    when(jdiReferenceType.name).thenReturn("Int")
    jdiReferenceType
  }

  lazy val longType: LongType = {
    val jdiReferenceType = mock(classOf[LongType])
    when(jdiReferenceType.name).thenReturn("Long")
    jdiReferenceType
  }

  lazy val shortType: ShortType = {
    val jdiReferenceType = mock(classOf[ShortType])
    when(jdiReferenceType.name).thenReturn("Shorts")
    jdiReferenceType
  }

  def referenceType(fullTypeName: String): ReferenceType = {
    val jdiReferenceType = mock(classOf[ReferenceType])
    when(jdiReferenceType.name).thenReturn(fullTypeName)
    jdiReferenceType
  }

  def arrayType(elementType: Type): ArrayType = {
    val jdiReferenceType = mock(classOf[ArrayType])
    when(jdiReferenceType.componentType).thenReturn(elementType)
    jdiReferenceType
  }

  def createJDIStackFrame(fullTypeName: String, methodName: String, params: List[Type] = Nil): StackFrame = {
    val jdiStackFrame = mock(classOf[StackFrame])
    val jdiLocation = mock(classOf[Location])
    when(jdiStackFrame.location).thenReturn(jdiLocation)

    val jdiMethod = mock(classOf[Method])
    when(jdiLocation.method).thenReturn(jdiMethod)
    when(jdiMethod.name).thenReturn(methodName)
    val declaringReferenceType = referenceType(fullTypeName)
    when(jdiMethod.declaringType).thenReturn(declaringReferenceType)

    import scala.collection.JavaConverters._
    when(jdiMethod.argumentTypes).thenReturn(params.asJava)

    jdiStackFrame
  }

}

class ScalaStackFrameTest {
  
  import ScalaStackFrameTest._

  @Before
  def initializeDebugPlugin() {
    if (DebugPlugin.getDefault == null) {
      new DebugPlugin
    }
  }

  @Test
  def getFullMethodNameNoParameters() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("package.name.TypeName", "methodName")

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName()", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameDefaultPackage() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("DefaultPackageTypeName", "methodName")

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "DefaultPackageTypeName.methodName()", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameBooleanParameter() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(booleanType))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(Boolean)", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameByteParameter() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(byteType))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(Byte)", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameCharParameter() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(charType))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(Char)", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameDoubleParameter() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(doubleType))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(Double)", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameFloatParameter() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(floatType))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(Float)", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameIntParameter() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(intType))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(Int)", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameLongParameter() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(longType))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(Long)", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameShortParameter() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(shortType))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(Short)", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameReferenceParameter() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(referenceType("some.package.ATypeName")))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(ATypeName)", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameArrayParameterReferenceElement() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(arrayType(referenceType("some.package.SomeTypeName"))))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(Array[SomeTypeName])", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameArrayParameterPrimitiveElement() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(arrayType(intType)))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(Array[Int])", scalaStackFrame.getMethodFullName)
  }

  @Test
  def getFullMethodNameMixedParameters() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("TypeName", "methodName", List(booleanType, arrayType(intType), floatType, referenceType("some.package.SomeTypeA"), arrayType(arrayType(referenceType("package.SomeTypeB")))))

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.methodName(Boolean, Array[Int], Float, SomeTypeA, Array[Array[SomeTypeB]])", scalaStackFrame.getMethodFullName)
  }
  
  @Test
  def getFullMethodNameWithEncoding() {
    val scalaThread = mock(classOf[ScalaThread])

    val jdiStackFrame = createJDIStackFrame("package.name.TypeName", "$colon$plus$minus$qmark")

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad full method name", "TypeName.:+-?()", scalaStackFrame.getMethodFullName)
  }
  
  @Test
  def getVariableNonStaticMethod() {
    import scala.collection.JavaConverters._
    
    val scalaThread = mock(classOf[ScalaThread])
    
    val jdiStackFrame = mock(classOf[StackFrame])
    val jdiLocation = mock(classOf[Location])
    when(jdiStackFrame.location).thenReturn(jdiLocation)
    val jdiMethod = mock(classOf[Method])
    when(jdiLocation.method).thenReturn(jdiMethod)
    when(jdiMethod.isStatic).thenReturn(false)
    val jdiLocalVariable = mock(classOf[LocalVariable])
    when(jdiStackFrame.visibleVariables).thenReturn(List(jdiLocalVariable, jdiLocalVariable, jdiLocalVariable, jdiLocalVariable).asJava)

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)
    
    val variables= scalaStackFrame.getVariables
    
    assertEquals("Bad number of variables", 5, variables.length)
    assertTrue("Bad type for 'this'", variables.head.isInstanceOf[ScalaThisVariable])
    variables.tail.foreach {v =>
      assertTrue("Bad local variable type", v.isInstanceOf[ScalaLocalVariable])
    }
  }
  
  @Test
  def getVariableStaticMethod() {
    import scala.collection.JavaConverters._
    
    val scalaThread = mock(classOf[ScalaThread])
    
    val jdiStackFrame = mock(classOf[StackFrame])
    val jdiLocation = mock(classOf[Location])
    when(jdiStackFrame.location).thenReturn(jdiLocation)
    val jdiMethod = mock(classOf[Method])
    when(jdiLocation.method).thenReturn(jdiMethod)
    when(jdiMethod.isStatic).thenReturn(true)
    val jdiLocalVariable = mock(classOf[LocalVariable])
    when(jdiStackFrame.visibleVariables).thenReturn(List(jdiLocalVariable, jdiLocalVariable, jdiLocalVariable).asJava)

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)
    
    val variables= scalaStackFrame.getVariables
    
    assertEquals("Bad number of variables", 3, variables.length)
    variables.foreach {v =>
      assertTrue("Bad local variable type", v.isInstanceOf[ScalaLocalVariable])
    }
  }

}