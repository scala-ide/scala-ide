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
import org.junit.Ignore
import scala.tools.eclipse.debug.BaseDebuggerActor
import org.junit.After
import scala.tools.eclipse.debug.PoisonPill

object ScalaThreadTest {
  private def createThreadGroup() = {
    val jdiThreadGroup = mock(classOf[ThreadGroupReference])
    when(jdiThreadGroup.name).thenReturn("some")
    jdiThreadGroup;
  }

  final private val WaitingStep = 50

  private def waitUntil(condition: => Boolean, timeout: Int) {
    val timeoutEnd = System.currentTimeMillis() + timeout
    while (!condition) {
      assertTrue("Timed out before condition was satisfied", System.currentTimeMillis() < timeoutEnd)
      Thread.sleep(WaitingStep)
    }
  }

}

/**
 * Tests for ScalaThread.
 */
class ScalaThreadTest {
  import ScalaThreadTest._

  /**
   * The actor associated to the debug target currently being tested.
   */
  var actor: Option[BaseDebuggerActor] = None

  @Before
  def initializeDebugPlugin() {
    if (DebugPlugin.getDefault == null) {
      new DebugPlugin
    }
  }

  @After
  def cleanupActor() {
    actor.foreach(_ ! PoisonPill)
    actor = None
  }

  private def anonDebugTarget: ScalaDebugTarget = {
    val debugTarget = mock(classOf[ScalaDebugTarget])
    val debugTargetActor = mock(classOf[BaseDebuggerActor])
    when(debugTarget.eventActor).thenReturn(debugTargetActor)
    debugTarget
  }

  private def createThread(jdiThread: ThreadReference): ScalaThread = {
    val thread = ScalaThread(anonDebugTarget, jdiThread)
    actor = Some(thread.eventActor)
    thread
  }

  @Test
  def getName() {
    val jdiThread = mock(classOf[ThreadReference])

    when(jdiThread.name).thenReturn("some test string")
    val jdiThreadGroup = createThreadGroup()
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)

    val thread = createThread(jdiThread)

    assertEquals("Bad thread name", "some test string", thread.getName)
  }

  @Test
  def vmDisconnectedExceptionOnGetName() {
    val jdiThread = mock(classOf[ThreadReference])

    when(jdiThread.name).thenThrow(new VMDisconnectedException)
    val jdiThreadGroup = createThreadGroup()
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)

    val thread = createThread(jdiThread)

    assertEquals("Bad thread name on VMDisconnectedException", "<disconnected>", thread.getName)
  }

  @Test
  def objectCollectedExceptionOnGetName() {
    val jdiThread = mock(classOf[ThreadReference])

    when(jdiThread.name).thenThrow(new ObjectCollectedException)
    val jdiThreadGroup = createThreadGroup()
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)

    val thread = createThread(jdiThread)

    assertEquals("Bad thread name", "<garbage collected>", thread.getName)
  }

  /**
   * Check that the underlying thread is resume only once when the resume() method is called.
   * See #1001199
   * FIXME: With the changes for #1001308, this test is not working correctly any more. It was relying on the fact that #getStackFrames was generating a sync call.
   * See #1001321
   */
  @Ignore
  @Test
  def threadResumedOnlyOnce_1001199() {
    val jdiThread = mock(classOf[ThreadReference])

    val thread = createThread(jdiThread)

    thread.resume()

    // using getStackFrame, which is synchronous, to wait for the ResumeFromScala to be processed
    thread.getStackFrames

    verify(jdiThread, times(1)).resume()
  }

  /**
   * Check that calling #getStackFrame doesn't create a freeze. It used to be making a sync call to the actor, even if it was shutdown.
   * #1001308
   */
  @Test(timeout = 2000)
  def getStackFramesFreeze() {
    
    val jdiThread = mock(classOf[ThreadReference])

    val thread = createThread(jdiThread)

    thread.eventActor ! ScalaThreadActor.TerminatedFromScala
    thread.getStackFrames
  }

}
