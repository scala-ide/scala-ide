package org.scalaide.debug.internal.model

import org.junit.Test
import org.junit.Assert._
import org.mockito.Mockito._
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget
import com.sun.jdi.VirtualMachine
import com.sun.jdi.ThreadReference
import java.util.ArrayList
import org.eclipse.debug.core.DebugPlugin
import org.junit.Before
import com.sun.jdi.event.ThreadStartEvent
import com.sun.jdi.request.EventRequestManager
import com.sun.jdi.request.ThreadStartRequest
import com.sun.jdi.request.ThreadDeathRequest
import org.eclipse.debug.core.Launch
import com.sun.jdi.event.EventQueue
import org.scalaide.debug.internal.BaseDebuggerActor
import org.scalaide.debug.internal.PoisonPill
import com.sun.jdi.event.VMDeathEvent
import org.junit.After
import org.junit.Assert
import org.junit.BeforeClass

object ScalaDebugTargetTest {
  var debugTarget: ScalaDebugTarget = _

  /**
   * Create a debug target with most of the JDI implementation mocked
   */
  @BeforeClass
  def createDebugTarget(): Unit = {
    val virtualMachine = mock(classOf[VirtualMachine])
    when(virtualMachine.allThreads).thenReturn(new ArrayList[ThreadReference]())
    val eventRequestManager = mock(classOf[EventRequestManager])
    when(virtualMachine.eventRequestManager).thenReturn(eventRequestManager)
    when(virtualMachine.eventQueue).thenReturn(mock(classOf[EventQueue]))
    val threadStartRequest = mock(classOf[ThreadStartRequest])
    when(eventRequestManager.createThreadStartRequest).thenReturn(threadStartRequest)
    val threadDeathRequest = mock(classOf[ThreadDeathRequest])
    when(eventRequestManager.createThreadDeathRequest).thenReturn(threadDeathRequest)
    debugTarget = ScalaDebugTarget(virtualMachine, mock(classOf[Launch]), null, allowDisconnect = false, allowTerminate = true)
  }
}

class ScalaDebugTargetTest {
  import ScalaDebugTargetTest._
  import org.scalaide.debug.internal.TestFutureUtil._
  import scala.concurrent.ExecutionContext.Implicits.global

  @Before
  def initializeDebugPlugin(): Unit = {
    if (DebugPlugin.getDefault == null) {
      new DebugPlugin
    }
  }

  @Test
  def threadNotTwiceInList(): Unit = {
    val ThreadName = "thread name"

    val event = mock(classOf[ThreadStartEvent])
    val thread = mock(classOf[ThreadReference])
    when(event.thread).thenReturn(thread)
    when(thread.name).thenReturn(ThreadName)

    whenReady(debugTarget.subordinate.handle(event)) { _ =>
      val threads1 = debugTarget.getThreads
      assertEquals("Wrong number of threads", 1, threads1.length)
      assertEquals("Wrong thread name", ThreadName, threads1(0).getName)
    }

    // a second start event should not result in a duplicate entry
    whenReady(debugTarget.subordinate.handle(event)) { _ =>
      val threads2 = debugTarget.getThreads
      assertEquals("Wrong number of threads", 1, threads2.length)
      assertEquals("Wrong thread name", ThreadName, threads2(0).getName)
    }
  }

  /**
   * Check that calling #getThreads doesn't create a freeze. It used to be making a sync call to the actor, even if it was shutdown.
   * #1001308
   */
  @Test(timeout = 2000)
  def getThreadsFreeze(): Unit = {
    whenReady(debugTarget.subordinate.handle(mock(classOf[VMDeathEvent]))) { _ =>
      debugTarget.getThreads
    }
  }
}
