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
import org.scalaide.debug.internal.BaseDebuggerActor
import org.scalaide.debug.internal.EclipseDebugEvent
import org.scalaide.debug.internal.PoisonPill
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

object DebugTargetTerminationTest {
  final val LatchTimeout = 5000L

  object TerminationListenerActor {
    private case class LinksTo(actors: Set[Suppress.DeprecatedWarning.Actor])
    def apply(linkTo: Suppress.DeprecatedWarning.Actor)(onTermination: () => Unit): Suppress.DeprecatedWarning.Actor = apply(Set(linkTo))(onTermination)
    def apply(linksTo: Set[Suppress.DeprecatedWarning.Actor])(onTermination: () => Unit): Suppress.DeprecatedWarning.Actor = {
      val actor = new TerminationListenerActor(onTermination)
      actor.start()
      // Block the current thread and make sure that the `linksTo` actors are linked together with the `actor`.
      // This is needed to guarantee that the `linksTo` are not `Terminated` before the `actor` is linked to them.
      // Failing to do so can cause spurious test failures, so be careful if you change this.
      actor !? LinksTo(linksTo)
      actor
    }
  }
  private class TerminationListenerActor(onTermination: () => Unit) extends Suppress.DeprecatedWarning.DaemonActor {
    import TerminationListenerActor.LinksTo

    var linkedActors: Set[Suppress.DeprecatedWarning.Actor] = _
    var stopped = false
    var terminatedLinks: Set[Suppress.DeprecatedWarning.AbstractActor] = Set()

    override def act() = {
      Suppress.DeprecatedWarning.Actor.self.trapExit = true
      loopWhile(!stopped) {
        react {
          case LinksTo(actors) =>
            actors foreach { actor => assertThat(actor.toString, actor.getState, is(not(Suppress.DeprecatedWarning.State.Terminated))) }
            actors.foreach(link(_))
            linkedActors = actors
            reply(())
          case exit: Suppress.DeprecatedWarning.Exit =>
            terminatedLinks += exit.from
            if (linkedActors.forall(terminatedLinks contains _)) {
              stopped = true
              linkedActors.foreach(Suppress.DeprecatedWarning.Actor.self.unlink(_))
              onTermination()
            }
        }
      }
    }
  }
  /**A dummy runtime exception for testing purposes. You should use throw this exception only when testing code that is expected
   * to gracefully recover from generic exceptions.*/
  object ExceptionForTestingPurposes_ThisIsOk extends RuntimeException with scala.util.control.NoStackTrace
}

class DebugTargetTerminationTest extends HasLogger {
  import DebugTargetTerminationTest.ExceptionForTestingPurposes_ThisIsOk
  import DebugTargetTerminationTest.TerminationListenerActor
  import DebugTargetTerminationTest.LatchTimeout

  var virtualMachine: VirtualMachine = _
  var eventQueue: EventQueue = _
  var debugTarget: ScalaDebugTarget = _

  @Before
  def initializeDebugPlugin(): Unit = {
    if (DebugPlugin.getDefault == null) {
      new DebugPlugin
    }
  }

  @Before
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

  implicit def handleDebugEvents(f: Array[DebugEvent] => Unit): IDebugEventSetListener = new IDebugEventSetListener {
    override def handleDebugEvents(events: Array[DebugEvent]): Unit = f(events)
  }

  private def checkGracefulTerminationOf(actors: Suppress.DeprecatedWarning.Actor*) = new {
    def when(body: => Unit): Unit = {
      withCountDownLatch(1) { latch =>
        // setup test actor that listens for termination
        TerminationListenerActor(actors.toSet) { () =>
          // verify
          for (a <- actors)
            assertThat(a + "expected to be terminated, but it's alive.", a.getState, is(Suppress.DeprecatedWarning.State.Terminated))
          latch.countDown()
        }
        body
      }
    }
  }

  private def withCountDownLatch(counter: Int, timeout: Long = LatchTimeout)(body: CountDownLatch => Unit): Unit = {
    val latch = new CountDownLatch(counter)
    body(latch)
    latch.await(timeout, TimeUnit.SECONDS)
  }

  @Test
  def abruptTerminationOf_DebugTargetActor_is_gracefully_handled(): Unit = {
    val debugTargetActor = debugTarget.companionActor

    checkGracefulTerminationOf(debugTargetActor) when {
      debugTarget.terminate()
      assertTrue(debugTarget.isTerminated)
    }
  }

  @Test
  def normalTerminationOf_DebugTargetActor(): Unit = {
    val debugTargetActor = debugTarget.companionActor

    checkGracefulTerminationOf(debugTargetActor) when {
      debugTargetActor ! PoisonPill
    }
  }

  @Test
  def normalTerminationOf_DebugTargetActor_triggers_BreakpointManagerActor_termination(): Unit = {
    val debugTargetActor = debugTarget.companionActor
    val breapointManagerActor = debugTarget.breakpointManager.companionActor

    checkGracefulTerminationOf(breapointManagerActor) when {
      debugTargetActor ! PoisonPill
    }
  }

  @Test
  def normalTerminationOf_BreakpointManagerActor_triggers_DebugTargetActor_termination(): Unit = {
    val debugTargetActor = debugTarget.companionActor
    val breapointManagerActor = debugTarget.breakpointManager.companionActor

    checkGracefulTerminationOf(debugTargetActor) when {
      breapointManagerActor ! PoisonPill
    }
  }

  @Test
  def normalTerminationOf_DebugTargetActor_triggers_JdiEventDispatcherActor_termination(): Unit = {
    val debugTargetActor = debugTarget.companionActor
    val jdiEventDispatcherActor = debugTarget.eventDispatcher.companionActor

    checkGracefulTerminationOf(jdiEventDispatcherActor) when {
      debugTargetActor ! PoisonPill
    }
  }

  @Test
  def normalTerminationOf_JdiEventDispatcherActor_triggers_DebugTargetActor_termination(): Unit = {
    val debugTargetActor = debugTarget.companionActor
    val jdiEventDispatcherActor = debugTarget.eventDispatcher.companionActor

    checkGracefulTerminationOf(debugTargetActor) when {
      jdiEventDispatcherActor ! PoisonPill
    }
  }

  @Test
  def throwing_VMDisconnectedException_in_JdiEventDispatcher_triggers_DebugTargetActor_termination(): Unit = {
    val debugTargetActor = debugTarget.companionActor
    val jdiEventDispatcherActor = debugTarget.eventDispatcher.companionActor

    checkGracefulTerminationOf(debugTargetActor, jdiEventDispatcherActor) when {
      //eventQueue.remove happens every 1sec
      when(eventQueue.remove(anyLong)).thenThrow(new VMDisconnectedException).thenReturn(null)
    }
  }

  @Test
  def throwing_GenericException_in_JdiEventDispatcher_doesNot_terminates_linked_actors(): Unit = {
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

    // this is a test actor that terminates its execution when the `dummyEvent` is received
    withCountDownLatch(1) { latch =>
      val testActor = Suppress.DeprecatedWarning.Actor.actor {
        Suppress.DeprecatedWarning.Actor.loop {
          Suppress.DeprecatedWarning.Actor.react {
            case event if event == dummyEvent => latch.countDown()
          }
        }
      }
      // Ensures that the `ScalaJDiEventDispatcher` forwards `dummyEventRequest` to the `testActor`
      debugTarget.eventDispatcher.setActorFor(testActor, dummyEventRequest)

      // The `ScalaJDiEventDispatcher` thread should not dies when an unhandled exception is thrown, hence
      // the `testActor` will eventually receive the `eventSet` message the decrease the `latch` counter.
      when(eventQueue.remove(anyLong)).thenThrow(ExceptionForTestingPurposes_ThisIsOk).thenReturn(eventSet)
    }
    // don't leave the JDI event dispatcher running, otherwise the mocked queue will eat up all
    // the heap and eventually start failing subsequent tests due to timeouts or OOM
    checkGracefulTerminationOf(debugTarget.companionActor) when {
      debugTarget.companionActor ! mock(classOf[VMDisconnectEvent])
    }
  }

  @Test
  def anUnhandledExceptionGrafeullyTerminatesAllLinkedActors(): Unit = {
    val debugTargetActor = debugTarget.companionActor

    val sut = new BaseDebuggerActor {
      override protected def postStart(): Unit = link(debugTargetActor)
      override def behavior: Behavior = {
        case _ => throw ExceptionForTestingPurposes_ThisIsOk
      }
    }
    sut.start()

    checkGracefulTerminationOf(debugTargetActor) when {
      sut ! 'msg
    }
  }
}
