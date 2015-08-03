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
import org.hamcrest.CoreMatchers.is
import org.hamcrest.CoreMatchers.not
import org.junit.Assert._
import org.junit.Before
import org.junit.Test
import org.mockito.Matchers.anyLong
import org.mockito.Mockito._
import org.scalaide.debug.internal.EclipseDebugEvent
import org.scalaide.debug.internal.ScalaDebugTestSession
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.Suppress
import com.sun.jdi.ThreadReference
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.Event
import com.sun.jdi.event.EventQueue
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.VMDisconnectEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.EventRequestManager
import com.sun.jdi.request.ThreadDeathRequest
import com.sun.jdi.request.ThreadStartRequest
import org.junit.Assert
import com.sun.jdi.event.VMStartEvent
import scala.concurrent.Future
import scala.concurrent.Await
import org.junit.Ignore
import org.junit.BeforeClass

object DebugTargetTerminationTest {
  final val LatchTimeout = 5000L

  /**
   * A dummy runtime exception for testing purposes. You should use throw this exception only when testing code that is expected
   * to gracefully recover from generic exceptions.
   */
  object ExceptionForTestingPurposes_ThisIsOk extends RuntimeException with scala.util.control.NoStackTrace

  var virtualMachine: VirtualMachine = _
  var eventQueue: EventQueue = _
  var debugTarget: ScalaDebugTarget = _

  def withCountDownLatch(counter: Int, timeout: Long = LatchTimeout)(body: CountDownLatch => Unit): Boolean = {
    val latch = new CountDownLatch(counter)
    body(latch)
    latch.await(timeout, TimeUnit.SECONDS)
  }

  @BeforeClass
  def initializeDebugTarget(): Unit = {
    virtualMachine = mock(classOf[VirtualMachineImpl])
    when(virtualMachine.allThreads).thenReturn(new ArrayList[ThreadReference]())
    val eventRequestManager = mock(classOf[EventRequestManager])
    when(virtualMachine.eventRequestManager).thenReturn(eventRequestManager)
    eventQueue = mock(classOf[EventQueue])
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

        debugTarget = ScalaDebugTarget(virtualMachine, mock(classOf[Launch]), mock(classOf[IProcess]), allowDisconnect = false, allowTerminate = true)
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

  private def assertDebugTargetTerminated(precondition: => Unit): Unit = {
    Assert.assertTrue(withCountDownLatch(1) { latch =>
      precondition
      while (!debugTarget.isTerminated) {}
      latch.countDown()
    })
  }

  import org.scalaide.debug.internal.TestFutureUtil._
  import scala.concurrent.ExecutionContext.Implicits.global

  @Test
  def abruptTerminationOf_DebugTarget_is_gracefully_handled(): Unit = {
    debugTarget.terminate()
    assertTrue(debugTarget.isTerminated)
  }

  @Test
  def normalTerminationOf_DebugTargetSubordinate(): Unit = {
    whenReady(debugTarget.dispose()) { Unit =>
      Assert.assertTrue(debugTarget.isTerminated)
    }
  }

  @Test
  def normalTerminationOf_DebugTargetSubordinate_triggers_JdiEventDispatcher_termination(): Unit = {
    val jdiEventDispatcher = debugTarget.eventDispatcher

    whenReady(debugTarget.dispose()) { _ =>
      Assert.assertFalse(jdiEventDispatcher.isRunning)
    }
  }

  @Test
  def normalTerminationOf_JdiEventDispatcherSubordinate_triggers_DebugTarget_termination(): Unit = {
    val jdiEventDispatcherSubordinate = debugTarget.eventDispatcher.subordinate

    whenReady(jdiEventDispatcherSubordinate.dispose()) { _ =>
      Assert.assertTrue(debugTarget.isTerminated())
    }
  }

  @Test
  def throwing_VMDisconnectedException_in_JdiEventDispatcher_triggers_DebugTarget_termination(): Unit = {
    when(eventQueue.remove(anyLong)).thenThrow(new VMDisconnectedException).thenReturn(null)

    assertDebugTargetTerminated {}
  }

  @Ignore
  @Test
  def throwing_GenericException_in_JdiEventDispatcher_doesNot_terminates_DebugTarget(): Unit = {
    // TODO: Implement
    // set up a dummy debugger request and event
    val dummyEventRequest: EventRequest = new EventRequestImpl("Dummy Event Request", virtualMachine.asInstanceOf[VirtualMachineImpl]) {
      override protected def eventKind(): Byte = 999.toByte //avoids collision with constants defined in org.eclipse.jdi.internal.event.EventImpl
    }
    val dummyEvent: Event = mock(classOf[Event])
    when(dummyEvent.request).thenReturn(dummyEventRequest)

    // prepare the eventSet instance to be returned by the `ScalaJDiEventDispatcher` *after* the exception is processed
    val eventSet = mock(classOf[EventSet])
    val eventIterator = new EventIteratorImpl(Arrays.asList(dummyEvent).listIterator())
    when(eventSet.eventIterator()).thenReturn(eventIterator)

  }

  @Test
  def anUnhandledExceptionGrafeullyTerminatesLinkedScalaJdiDispatcherAndDebugTarget(): Unit = {
    // TODO: Implement
  }
}
