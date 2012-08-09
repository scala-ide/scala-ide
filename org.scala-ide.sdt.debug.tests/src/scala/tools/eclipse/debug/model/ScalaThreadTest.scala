package scala.tools.eclipse.debug.model

import org.junit.Test
import com.sun.jdi.ThreadReference
import org.mockito.Mockito._
import org.junit.Assert._
import com.sun.jdi.VMDisconnectedException
import org.junit.Before
import org.eclipse.debug.core.DebugPlugin
import com.sun.jdi.ObjectCollectedException
import com.sun.jdi.ThreadGroupReference

object ScalaThreadTest {
  private def createThreadGroup() = {
    val jdiThreadGroup = mock(classOf[ThreadGroupReference])
    when(jdiThreadGroup.name).thenReturn("some")
    jdiThreadGroup;
  }
}

/**
 * Tests for ScalaThread.
 */
class ScalaThreadTest {
  import ScalaThreadTest._

  @Before
  def initializeDebugPlugin() {
    if (DebugPlugin.getDefault == null) {
      new DebugPlugin
    }
  }

  @Test
  def getName() {
    val jdiThread = mock(classOf[ThreadReference])

    when(jdiThread.name).thenReturn("some test string")
    val jdiThreadGroup = createThreadGroup();
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)

    val thread = ScalaThread(null, jdiThread)

    assertEquals("Bad thread name", "some test string", thread.getName)
  }

  @Test
  def vmDisconnectedExceptionOnGetName() {
    val jdiThread = mock(classOf[ThreadReference])

    when(jdiThread.name).thenThrow(new VMDisconnectedException)
    val jdiThreadGroup = createThreadGroup();
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)

    val thread = ScalaThread(null, jdiThread)

    assertEquals("Bad thread name on VMDisconnectedException", "<disconnected>", thread.getName)
  }

  @Test
  def objectCollectedExceptionOnGetName() {
    val jdiThread = mock(classOf[ThreadReference])

    when(jdiThread.name).thenThrow(new ObjectCollectedException)
    val jdiThreadGroup = createThreadGroup();
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)

    val thread = ScalaThread(null, jdiThread)

    assertEquals("Bad thread name", "<garbage collected>", thread.getName)
  }

  /**
   * Check that the underlying thread is resume only once when the resume() method is called.
   * See #1001199
   */
  @Test
  def threadResumedOnlyOnce_1001199() {
    val jdiThread = mock(classOf[ThreadReference])

    val thread = ScalaThread(null, jdiThread)

    thread.resume()

    // using getStackFrame, which is synchronous, to wait for the ResumeFromScala to be processed
    thread.getStackFrames

    verify(jdiThread, times(1)).resume()
  }

}