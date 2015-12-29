package org.scalaide.debug.internal.model

import java.util.ArrayList

import scala.concurrent.ExecutionContext.Implicits

import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.Launch
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.when
import org.scalaide.debug.internal.TestFutureUtil
import org.scalaide.debug.internal.launching.ScalaDebuggerConfiguration

import com.sun.jdi.ThreadReference
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.EventQueue
import com.sun.jdi.event.ThreadStartEvent
import com.sun.jdi.event.VMDeathEvent
import com.sun.jdi.event.VMStartEvent
import com.sun.jdi.request.ClassPrepareRequest
import com.sun.jdi.request.EventRequestManager
import com.sun.jdi.request.ThreadDeathRequest
import com.sun.jdi.request.ThreadStartRequest

object ScalaDebugTargetTest {
  /**
   * Create a debug target with most of the JDI implementation mocked
   */
  def createDebugTarget(virtualMachine: VirtualMachine = mock(classOf[VirtualMachine])): ScalaDebugTarget = {
    when(virtualMachine.allThreads).thenReturn(new ArrayList[ThreadReference]())
    val eventRequestManager = mock(classOf[EventRequestManager])
    when(virtualMachine.eventRequestManager).thenReturn(eventRequestManager)
    when(virtualMachine.eventQueue).thenReturn(mock(classOf[EventQueue]))
    val threadStartRequest = mock(classOf[ThreadStartRequest])
    when(eventRequestManager.createThreadStartRequest).thenReturn(threadStartRequest)
    val threadDeathRequest = mock(classOf[ThreadDeathRequest])
    when(eventRequestManager.createThreadDeathRequest).thenReturn(threadDeathRequest)
    val classPrepareRequest = mock(classOf[ClassPrepareRequest])
    when(eventRequestManager.createClassPrepareRequest).thenReturn(classPrepareRequest)
    val launch = mock(classOf[Launch])
    val launchConfiguration = mock(classOf[ILaunchConfiguration])
    when(launch.getLaunchConfiguration).thenReturn(launchConfiguration)
    when(launchConfiguration.getAttribute(ScalaDebuggerConfiguration.LaunchWithAsyncDebugger, false)).thenReturn(true)
    ScalaDebugTarget(virtualMachine, launch, null, allowDisconnect = false, allowTerminate = true)
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
    val debugTarget = createDebugTarget()

    whenReady(debugTarget.subordinate.handle(mock(classOf[VMStartEvent]))) { _ =>
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

    debugTarget.terminate()
    waitForConditionOrTimeout(debugTarget.isTerminated)
  }

  /**
   * Check that calling #getThreads doesn't create a freeze. It used to be making a sync call to the actor, even if it was shutdown.
   * #1001308
   */
  @Test(timeout = 2000)
  def getThreadsFreeze(): Unit = {
    val debugTarget = createDebugTarget()

    whenReady(debugTarget.subordinate.handle(mock(classOf[VMDeathEvent]))) { _ =>
      debugTarget.getThreads
    }

    debugTarget.terminate()
    waitForConditionOrTimeout(debugTarget.isTerminated)
  }
}
