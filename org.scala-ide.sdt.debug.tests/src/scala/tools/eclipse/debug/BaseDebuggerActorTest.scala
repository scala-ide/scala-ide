package scala.tools.eclipse.debug

import java.util.concurrent.CountDownLatch
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.After

object BaseDebuggerActorTest {
  /** An empty partial function, i.e., it's a function with an empty domain. */
  private class NullPartialFunction[T, U] extends PartialFunction[T, U] {
    override def isDefinedAt(x: T): Boolean = false
    override def apply(x: T): U = throw new IllegalStateException
  }
}

@RunWith(classOf[JUnit4])
class BaseDebuggerActorTest {
  import BaseDebuggerActorTest.NullPartialFunction

  /** The actor currently being tested.
   */
  var sut: BaseDebuggerActor = null

  @After
  def actorCleanup() {
    if (sut != null) {
      sut ! PoisonPill
    }
    sut = null
  }

  @Test(timeout = 1000)
  def postStartIsAlwaysExecutedBeforeTheActorProcessesTheFirstMessage() {
    //setting up test
    val latch = new CountDownLatch(1)
    sut = new BaseDebuggerActor {
      override protected def postStart(): Unit = latch.countDown()
      override protected def behavior: Behavior = {
        case _ =>
          if (latch.getCount() != 0)
            fail("Failed precondition: `postStart` method is guaranteed to be called before the first message is processed")
      }
    }

    // send a message to the actor before starting it
    sut ! 'msg
    sut.start()
    latch.await()
  }

  @Test(timeout = 1000)
  def preExitIsAlwaysExecutedBeforeTheActorIsStopped() {
    //setting up test
    val latch = new CountDownLatch(1)
    sut = new BaseDebuggerActor {
      override protected def behavior: Behavior = new NullPartialFunction // i.e., the exitBehavior is always executed!
      override protected def preExit(): Unit = latch.countDown()
    }

    sut.start()
    sut ! PoisonPill
    latch.await()
  }

  @Test(timeout = 1000)
  def itIsNotPossibleToRemoveTheInitialActorBehavior() {
    val latch = new CountDownLatch(1)
    sut = new BaseDebuggerActor {
      override def postStart(): Unit = unbecome()
      override def behavior: Behavior = {
        case _ => latch.countDown()
      }
    }

    sut.start()
    sut ! 'msg
    latch.await()
  }

  @Test(timeout = 1000)
  def callingBecomeChangesTheActorBehavior() {
    val latch = new CountDownLatch(1)
    sut = new BaseDebuggerActor {
      override def behavior: Behavior = {
        case 'firstMsg => become { case 'secondMsg => latch.countDown() }
      }
    }

    sut.start()

    sut ! 'firstMsg // this will trigger a change in the actor's behavior
    sut ! 'secondMsg // this will trigger a decrement in the latch counter

    latch.await()
  }

  @Test(timeout = 1000)
  def poisonedActorIsNoLongerAllowedToModifyItsBehavior() {
    val latch = new CountDownLatch(1)
    sut = new BaseDebuggerActor {
      override def behavior: Behavior = {
        case 'poison =>
          poison() // poisoning an actor forces it to process only termination messages!
          // hence the `become` call should be ignored.
          become { case _ => fail("You can't heal after being poisoned!") }
      }

      override def preExit(): Unit = latch.countDown()
    }

    sut.start()

    sut ! 'poison
    sut ! 'msg

    latch.await()
  }
}