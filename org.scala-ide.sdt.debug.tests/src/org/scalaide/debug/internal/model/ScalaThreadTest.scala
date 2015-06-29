package org.scalaide.debug.internal.model

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
import org.scalaide.debug.internal.BaseDebuggerActor
import org.junit.After
import org.scalaide.debug.internal.PoisonPill

object ScalaThreadTest {
  private def createThreadGroup() = {
    val jdiThreadGroup = mock(classOf[ThreadGroupReference])
    when(jdiThreadGroup.name).thenReturn("some")
    jdiThreadGroup;
  }

  final private val WaitingStep = 50
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
  def initializeDebugPlugin(): Unit = {
    if (DebugPlugin.getDefault == null) {
      new DebugPlugin
    }
  }

  @After
  def cleanupActor(): Unit = {
    actor.foreach(_ ! PoisonPill)
    actor = None
  }

  private def anonDebugTarget: ScalaDebugTarget = {
    val debugTarget = mock(classOf[ScalaDebugTarget])
    val debugTargetActor = mock(classOf[BaseDebuggerActor])
    when(debugTarget.companionActor).thenReturn(debugTargetActor)
    debugTarget
  }

  private def createThread(jdiThread: ThreadReference): ScalaThread = {
    val thread = ScalaThread(anonDebugTarget, jdiThread)
    actor = Some(thread.companionActor)
    thread
  }

  @Test
  def getName(): Unit = {
    val jdiThread = mock(classOf[ThreadReference])

    when(jdiThread.name).thenReturn("some test string")
    val jdiThreadGroup = createThreadGroup()
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)

    val thread = createThread(jdiThread)

    assertEquals("Bad thread name", "some test string", thread.getName)
  }

  @Test
  def vmDisconnectedExceptionOnGetName(): Unit = {
    val jdiThread = mock(classOf[ThreadReference])

    when(jdiThread.name).thenThrow(new VMDisconnectedException)
    val jdiThreadGroup = createThreadGroup()
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)

    val thread = createThread(jdiThread)

    assertEquals("Bad thread name on VMDisconnectedException", "Error retrieving name", thread.getName)
  }

  @Test
  def objectCollectedExceptionOnGetName(): Unit = {
    val jdiThread = mock(classOf[ThreadReference])

    when(jdiThread.name).thenThrow(new ObjectCollectedException)
    val jdiThreadGroup = createThreadGroup()
    when(jdiThread.threadGroup).thenReturn(jdiThreadGroup)

    val thread = createThread(jdiThread)

    assertEquals("Bad thread name", "Error retrieving name", thread.getName)
  }

  /**
   * Check that the underlying thread is resume only once when the resume() method is called.
   * See #1001199
   * FIXME: With the changes for #1001308, this test is not working correctly any more. It was relying on the fact that #getStackFrames was generating a sync call.
   * See #1001321
   */
  @Ignore
  @Test
  def threadResumedOnlyOnce_1001199(): Unit = {
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
  def getStackFramesFreeze(): Unit = {

    val jdiThread = mock(classOf[ThreadReference])

    val thread = createThread(jdiThread)

    thread.companionActor ! ScalaThreadActor.TerminatedFromScala
    thread.getStackFrames
  }

}
