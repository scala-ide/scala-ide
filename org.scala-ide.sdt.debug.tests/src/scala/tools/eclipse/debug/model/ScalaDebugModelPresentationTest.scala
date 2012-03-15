package scala.tools.eclipse.debug.model

import org.eclipse.debug.core.DebugPlugin
import org.junit.Assert._
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito._
import com.sun.jdi.ThreadReference
import com.sun.jdi.ThreadGroupReference
import com.sun.jdi.StackFrame
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.StringReference
import com.sun.jdi.ArrayReference
import com.sun.jdi.IntegerValue
import com.sun.jdi.Value
import com.sun.jdi.ObjectReference
import com.sun.jdi.ClassType

class ScalaDebugModelPresentationTest {

  val modelPres = new ScalaDebugModelPresentation

  @Before
  def initializeDebugPlugin() {
    if (DebugPlugin.getDefault == null) {
      new DebugPlugin
    }
  }

  @Test
  def scalaThreadName() {
    val jdiThread = mock(classOf[ThreadReference])
    when(jdiThread.name).thenReturn("thread name")
    val jdiThreadGroup = mock(classOf[ThreadGroupReference])
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)
    when(jdiThreadGroup.name).thenReturn("not system")

    val scalaThread = new ScalaThread(null, jdiThread)

    assertEquals("Bad display name for Scala thread", "Thread [thread name]", modelPres.getText(scalaThread))
  }

  @Test
  def scalaThreadNameForSystemThread() {
    val jdiThread = mock(classOf[ThreadReference])
    when(jdiThread.name).thenReturn("system thread name")
    val jdiThreadGroup = mock(classOf[ThreadGroupReference])
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)
    when(jdiThreadGroup.name).thenReturn("system")

    val scalaThread = new ScalaThread(null, jdiThread)

    assertEquals("Bad display name for Scala system thread", "Deamon System Thread [system thread name]", modelPres.getText(scalaThread))
  }

  @Test
  def scalaStackFrame() {
    val scalaThread = mock(classOf[ScalaThread])

    import ScalaStackFrameTest._
    
    val jdiStackFrame = createJDIStackFrame("some.package.TypeName", "methodName", List(referenceType("a.b.ParamType1"), referenceType("a.b.ParamType2")))
    val location= jdiStackFrame.location
    when(location.lineNumber).thenReturn(42)

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad display name for Scala stack frame", "TypeName.methodName(ParamType1, ParamType2) line: 42", modelPres.getText(scalaStackFrame))
  }
  
  @Test
  def scalaStackFrameLineNotAvailable() {
    val scalaThread = mock(classOf[ScalaThread])

    import ScalaStackFrameTest._
    
    val jdiStackFrame = createJDIStackFrame("some.package.TypeName", "methodName", Nil)
    val location= jdiStackFrame.location
    when(location.lineNumber).thenReturn(-1)

    val scalaStackFrame = new ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad display name for Scala stack frame", "TypeName.methodName() line: not available", modelPres.getText(scalaStackFrame))
  }
  
  @Test
  def computeDetailNull() {
    val scalaValue = mock(classOf[ScalaNullValue])
    
    val computedDetail= ScalaDebugModelPresentation.computeDetail(scalaValue)
    
    assertEquals("Bad return value for computeDetail", "null", computedDetail)
  }
  
  @Test
  def computeDetailPrimitiveNotString() {
    val scalaValue = new ScalaPrimitiveValue(null, "a value", null)
    
    val computedDetail= ScalaDebugModelPresentation.computeDetail(scalaValue)
    
    assertEquals("Bad return value for computeDetail", "a value", computedDetail)
  }
  
  @Test
  def computeDetailString() {
    val stringReference = mock(classOf[StringReference])
    when(stringReference.value).thenReturn("a string value")
    
    val computedDetail= ScalaDebugModelPresentation.computeDetail(new ScalaStringReference(stringReference, null))
    
    assertEquals("Bad return value for computeDetail", "a string value", computedDetail)
  }
  
  @Test
  def computeDetailArrayOfPrimitive() {
    val arrayReference= mock(classOf[ArrayReference])
    import scala.collection.JavaConverters._
    val values= List(createIntValue(1), createIntValue(2), createIntValue(4)).asJava
    when(arrayReference.getValues).thenReturn(values)
    
    val computedDetail= ScalaDebugModelPresentation.computeDetail(new ScalaArrayReference(arrayReference, null))
    
    assertEquals("Bad return value for computeDetail", "Array(1, 2, 4)", computedDetail)
  }
  
  // TODO: test for array of object reference
  
  // -----

  def createIntValue(i: Int): Value = {
    val value= mock(classOf[IntegerValue])
    when(value.value).thenReturn(i)
    value
  }
  
}