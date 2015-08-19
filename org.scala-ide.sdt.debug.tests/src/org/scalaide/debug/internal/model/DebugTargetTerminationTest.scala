package org.scalaide.debug.internal.model

import java.util.ArrayList
import java.util.Arrays
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.IDebugEventSetListener
import org.eclipse.debug.core.Launch
import org.eclipse.debug.core.model.IProcess
import org.eclipse.jdi.internal.VirtualMachineImpl
import org.eclipse.jdi.internal.event.EventIteratorImpl
import org.eclipse.jdi.internal.request.EventRequestImpl
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Matchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.when
import org.scalaide.debug.internal.EclipseDebugEvent
import org.scalaide.debug.internal.JdiEventReceiver
import org.scalaide.debug.internal.ScalaDebugTestSession
import org.scalaide.debug.internal.TestFutureUtil.waitForConditionOrTimeout
import org.scalaide.logging.HasLogger

import com.sun.jdi.ThreadReference
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.Event
import com.sun.jdi.event.EventQueue
import com.sun.jdi.event.EventSet
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.EventRequestManager
import com.sun.jdi.request.ThreadDeathRequest
import com.sun.jdi.request.ThreadStartRequest

import DebugTargetTerminationTest.ExceptionForTestingPurposes_ThisIsOk
import DebugTargetTerminationTest.initializeDebugTarget

object DebugTargetTerminationTest {
  final val LatchTimeout = 500L

  /**
   * A dummy runtime exception for testing purposes. You should use throw this exception only when testing code that is expected
   * to gracefully recover from generic exceptions.
   */
  object ExceptionForTestingPurposes_ThisIsOk extends RuntimeException with scala.util.control.NoStackTrace

  def withCountDownLatch[T](counter: Int, timeout: Long = LatchTimeout)(body: CountDownLatch => T): T = {
    val latch = new CountDownLatch(counter)
    val result = body(latch)
    if (latch.await(timeout, TimeUnit.SECONDS)) result else throw new AssertionError()
  }

  def initializeDebugTarget(virtualMachine: VirtualMachine = mock(classOf[VirtualMachineImpl]),
    eventQueue: EventQueue = mock(classOf[EventQueue])): ScalaDebugTarget = {
    when(virtualMachine.allThreads).thenReturn(new ArrayList[ThreadReference]())
    val eventRequestManager = mock(classOf[EventRequestManager])
    when(virtualMachine.eventRequestManager).thenReturn(eventRequestManager)
    when(virtualMachine.eventQueue).thenReturn(eventQueue)
    val threadStartRequest = mock(classOf[ThreadStartRequest])
    when(eventRequestManager.createThreadStartRequest).thenReturn(threadStartRequest)
    val threadDeathRequest = mock(classOf[ThreadDeathRequest])
    when(eventRequestManager.createThreadDeathRequest).thenReturn(threadDeathRequest)

    var debugEventListener: Option[IDebugEventSetListener] = None

    try {
      withCountDownLatch(1) { latch =>
        debugEventListener = Some(ScalaDebugTestSession.addDebugEventListener {
          case EclipseDebugEvent(DebugEvent.CREATE, _: ScalaDebugTarget) =>
            latch.countDown()
        })

        ScalaDebugTarget(virtualMachine, mock(classOf[Launch]), mock(classOf[IProcess]), allowDisconnect = false, allowTerminate = true)
      }
    } finally {
      debugEventListener.foreach(DebugPlugin.getDefault.removeDebugEventListener(_))
    }
  }
}

class DebugTargetTerminationTest extends HasLogger {
  import DebugTargetTerminationTest._

  @Before
  def initializeDebugPlugin(): Unit = {
    if (DebugPlugin.getDefault == null) {
      new DebugPlugin
    }
  }

  implicit def handleDebugEvents(f: Array[DebugEvent] => Unit): IDebugEventSetListener = new IDebugEventSetListener {
    override def handleDebugEvents(events: Array[DebugEvent]): Unit = f(events)
  }

  import org.scalaide.debug.internal.TestFutureUtil._
  import scala.concurrent.ExecutionContext.Implicits.global

  @Test
  def abruptTerminationOf_DebugTarget_is_gracefully_handled(): Unit = {
    val debugTarget = initializeDebugTarget()

    debugTarget.terminate()

    assertTrue(debugTarget.isTerminated)
  }

  @Test
  def normalTerminationOf_DebugTarget_triggers_JdiEventDispatcher_termination(): Unit = {
    val debugTarget = initializeDebugTarget()

    debugTarget.terminate()

    waitForConditionOrTimeout(!debugTarget.eventDispatcher.isRunning)
  }

  @Test
  def normalTerminationOf_JdiEventDispatcher_triggers_DebugTarget_termination(): Unit = {
    val debugTarget = initializeDebugTarget()

    debugTarget.eventDispatcher.dispose()

    waitForConditionOrTimeout(debugTarget.isTerminated)
  }

  @Test
  def throwing_VMDisconnectedException_in_JdiEventDispatcher_triggers_DebugTarget_termination(): Unit = {
    val eventQueue = mock(classOf[EventQueue])
    when(eventQueue.remove(anyLong)).thenThrow(new VMDisconnectedException).thenReturn(null)

    val debugTarget = initializeDebugTarget(eventQueue = eventQueue)

    waitForConditionOrTimeout(debugTarget.isTerminated)
  }

  @Test
  def throwing_GenericException_in_JdiEventDispatcher_doesNot_terminates_DebugTarget(): Unit = {
    // GIVEN:
    // set up a dummy debugger request and event
    val virtualMachine = mock(classOf[VirtualMachineImpl])
    val dummyEventRequest: EventRequest = new EventRequestImpl("Dummy Event Request", virtualMachine.asInstanceOf[VirtualMachineImpl]) {
      override protected def eventKind(): Byte = 999.toByte //avoids collision with constants defined in org.eclipse.jdi.internal.event.EventImpl
    }
    val dummyEvent: Event = mock(classOf[Event])
    when(dummyEvent.request).thenReturn(dummyEventRequest)
    // prepare the eventSet instance to be returned by the `ScalaJDiEventDispatcher` *after* the exception is processed
    val eventSet = mock(classOf[EventSet])
    val eventIterator = new EventIteratorImpl(Arrays.asList(dummyEvent).listIterator())
    when(eventSet.eventIterator()).thenReturn(eventIterator)
    // Create event queue with behavior
    val eventQueue = mock(classOf[EventQueue])
    when(eventQueue.remove(anyLong)).thenThrow(ExceptionForTestingPurposes_ThisIsOk).thenReturn(eventSet)

    // WHEN:
    val debugTarget = initializeDebugTarget(virtualMachine = virtualMachine, eventQueue = eventQueue)
    @volatile var gotDummyEvent = false
    val eventReceiver = new JdiEventReceiver {
      override protected def innerHandle: PartialFunction[Event, StaySuspended] = {
        case event if event == dummyEvent =>
          gotDummyEvent = true
          false
      }
    }
    debugTarget.eventDispatcher.register(eventReceiver, dummyEventRequest)

    // THEN:
    waitForConditionOrTimeout(gotDummyEvent)
    // Terminate debug target
    debugTarget.terminate()
    waitForConditionOrTimeout(!debugTarget.eventDispatcher.isRunning)
  }
}
