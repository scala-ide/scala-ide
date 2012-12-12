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
import scala.tools.eclipse.debug.BaseDebuggerActor
import org.junit.After
import scala.tools.eclipse.debug.PoisonPill

/**
 * More tests related of the ScalaDebugModelPresentation are in ScalaDebugComputeDetailTest.
 *
 */
class ScalaDebugModelPresentationTest {

  val modelPres = new ScalaDebugModelPresentation

  /**
   * The actor associated to the element currently being tested.
   */
  var actor: Option[BaseDebuggerActor] = None

  @Before
  def initializeDebugPlugin() {
    if (DebugPlugin.getDefault == null) {
      new DebugPlugin
    }
  }

  @After
  def actorCleanup() {
    actor.foreach(_ ! PoisonPill)
    actor = None
  }

  private def createThread(jdiThread: ThreadReference): ScalaThread = {
    val scalaThread = ScalaThread(null, jdiThread)
    actor = Some(scalaThread.companionActor)
    scalaThread
  }

  @Test
  def scalaThreadName() {
    val jdiThread = mock(classOf[ThreadReference])
    when(jdiThread.name).thenReturn("thread name")
    val jdiThreadGroup = mock(classOf[ThreadGroupReference])
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)
    when(jdiThreadGroup.name).thenReturn("not system")

    val scalaThread = createThread(jdiThread)

    assertEquals("Bad display name for Scala thread", "Thread [thread name]", modelPres.getText(scalaThread))
  }

  @Test
  def scalaThreadNameForSystemThread() {
    val jdiThread = mock(classOf[ThreadReference])
    when(jdiThread.name).thenReturn("system thread name")
    val jdiThreadGroup = mock(classOf[ThreadGroupReference])
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)
    when(jdiThreadGroup.name).thenReturn("system")

    val scalaThread = createThread(jdiThread)

    assertEquals("Bad display name for Scala system thread", "Deamon System Thread [system thread name]", modelPres.getText(scalaThread))
  }

  @Test
  def scalaStackFrame() {
    val scalaThread = mock(classOf[ScalaThread])

    import ScalaStackFrameTest._

    val jdiStackFrame = createJDIStackFrame("Lsome/package/TypeName;", "methodName", "(La/b/ParamType1;La/b/ParamType2;)V")
    val location = jdiStackFrame.location
    when(location.lineNumber).thenReturn(42)

    val scalaStackFrame = ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad display name for Scala stack frame", "TypeName.methodName(ParamType1, ParamType2) line: 42", modelPres.getText(scalaStackFrame))
  }

  @Test
  def scalaStackFrameLineNotAvailable() {
    val scalaThread = mock(classOf[ScalaThread])

    import ScalaStackFrameTest._

    val jdiStackFrame = createJDIStackFrame("Lsome/package/TypeName;", "methodName", "()V")
    val location = jdiStackFrame.location
    when(location.lineNumber).thenReturn(-1)

    val scalaStackFrame = ScalaStackFrame(scalaThread, jdiStackFrame)

    assertEquals("Bad display name for Scala stack frame", "TypeName.methodName() line: not available", modelPres.getText(scalaStackFrame))
  }

  @Test
  def computeDetailNull() {
    val scalaValue = mock(classOf[ScalaNullValue])

    val computedDetail = ScalaDebugModelPresentation.computeDetail(scalaValue)

    assertEquals("Bad return value for computeDetail", "null", computedDetail)
  }

  @Test
  def computeDetailPrimitiveNotString() {
    val scalaValue = new ScalaPrimitiveValue(null, "a value", null, null)

    val computedDetail = ScalaDebugModelPresentation.computeDetail(scalaValue)

    assertEquals("Bad return value for computeDetail", "a value", computedDetail)
  }

  @Test
  def computeDetailString() {
    val stringReference = mock(classOf[StringReference])
    when(stringReference.value).thenReturn("a string value")

    val computedDetail = ScalaDebugModelPresentation.computeDetail(new ScalaStringReference(stringReference, null))

    assertEquals("Bad return value for computeDetail", "a string value", computedDetail)
  }

  @Test
  def computeDetailArrayOfPrimitive() {
    val arrayReference = mock(classOf[ArrayReference])
    import scala.collection.JavaConverters._
    val values = List(createIntValue(1), createIntValue(2), createIntValue(4)).asJava
    when(arrayReference.length).thenReturn(3)
    when(arrayReference.getValues).thenReturn(values)

    val computedDetail = ScalaDebugModelPresentation.computeDetail(new ScalaArrayReference(arrayReference, null))

    assertEquals("Bad return value for computeDetail", "Array(1, 2, 4)", computedDetail)
  }

  /**
   * There is a bug in the JDT implementation of JDI.
   * ArrayReference#getValues() return an IndexOutOfBoundsException when called on an empty array.
   */
  @Test
  def computeDetailEmptyArrayJDIBug() {
    // simulate JDT/JDI bug
    val arrayReference = mock(classOf[ArrayReference])
    when(arrayReference.length).thenReturn(0)
    when(arrayReference.getValues).thenThrow(new IndexOutOfBoundsException)

    val computedDetail = ScalaDebugModelPresentation.computeDetail(new ScalaArrayReference(arrayReference, null))

    assertEquals("Bad return value for computeDetail", "Array()", computedDetail)
  }

  // -----

  def createIntValue(i: Int): Value = {
    val value = mock(classOf[IntegerValue])
    when(value.value).thenReturn(i)
    value
  }

}