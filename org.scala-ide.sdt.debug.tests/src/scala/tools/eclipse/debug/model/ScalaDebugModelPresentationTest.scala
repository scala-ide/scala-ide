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

}