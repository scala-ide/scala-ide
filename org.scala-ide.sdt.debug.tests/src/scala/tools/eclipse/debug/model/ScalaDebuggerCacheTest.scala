package scala.tools.eclipse.debug.model

import org.junit.Test
import org.junit.Assert._
import org.mockito.Mockito._
import scala.tools.eclipse.debug.ScalaDebugger
import com.sun.jdi.VirtualMachine
import com.sun.jdi.ReferenceType
import java.util.{ List => JList }
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.request.EventRequestManager
import com.sun.jdi.request.ClassPrepareRequest
import scala.tools.eclipse.util.TestUtils
import scala.actors.Actor
import scala.tools.eclipse.debug.BaseDebuggerActor
import java.util.concurrent.CountDownLatch
import scala.tools.eclipse.debug.PoisonPill
import org.junit.After
import java.util.concurrent.TimeUnit

class ScaladebugCacheTest {

  val toShutdown = scala.collection.mutable.ListBuffer[() => Unit]()

  @After
  def shutdownRegistered {

  }

  /** Test the returned nested class when no data was cached.
   *  Check a few possible false-positive in the regex match.
   */
  @Test
  def getNestedTypesNotCached() {
    val BaseName = "test01.a.Test"

    val (debugCache, classPrepareRequest1, classPrepareRequest2) = initMocks(BaseName, BaseName + "$", BaseName + "$a$b", BaseName + "$Test", BaseName + "$Test2", "test01.a.TestTest", "test01.b.Test", "a." + BaseName)

    assertTrue(debugCache.running)

    val actual = debugCache.getLoadedNestedTypes(BaseName)

    assertEquals("Wrong set of loaded nested types", Seq(BaseName, BaseName + "$", BaseName + "$Test", BaseName + "$Test2", BaseName + "$a$b"), toSortedListOfTypeName(actual))

    verifyCalls(debugCache, classPrepareRequest1, classPrepareRequest2, BaseName)

  }

  /** Test the returned nested class when a new nested class has been loaded after an initial request filled in the cache.
   */
  @Test
  def getNestedTypesUpdateCache() {
    val BaseName = "test02.a.Test"

    val (debugCache, classPrepareRequest1, classPrepareRequest2) = initMocks(BaseName, BaseName + "$", BaseName + "$a")

    val actual = debugCache.getLoadedNestedTypes(BaseName)

    assertEquals("Wrong set of loaded nested types", Seq(BaseName, BaseName + "$", BaseName + "$a"), toSortedListOfTypeName(actual))

    // 'receive' a ClassPrepareEvent from the VM
    debugCache.actor !? createClassPrepareEvent(BaseName + "$b")

    val actual2 = debugCache.getLoadedNestedTypes(BaseName)

    assertEquals("Wrong set of loaded nested types", Seq(BaseName, BaseName + "$", BaseName + "$a", BaseName + "$b"), toSortedListOfTypeName(actual2))

    verifyCalls(debugCache, classPrepareRequest1, classPrepareRequest2, BaseName)
  }

  /** Test that ClassPrepareEvent events are sent when needed, and not when no requested.
   */
  @Test
  def registerAndDeregisterListener() {
    val BaseName = "test03.a.Test"

    val (debugCache, classPrepareRequest1, classPrepareRequest2) = initMocks(BaseName, BaseName + "$", BaseName + "$a")

    // a latch listener actor. It releases the latch when it receives a ClassPrepareEvent
    val testActor = new BaseDebuggerActor {
      var latch = new CountDownLatch(1)

      override def behavior = {
        case e: ClassPrepareEvent =>
          latch.countDown()
      }

      def awaitLatch(time: Int): Boolean = {
        latch.await(time, TimeUnit.MILLISECONDS)
      }

      def resetLatch() {
        latch = new CountDownLatch(1)
      }
    }

    toShutdown += (() => { testActor ! PoisonPill })
    testActor.start

    // register listener, and check get data

    debugCache.registerClassPrepareEventListener(testActor, BaseName)

    // 'receive' a ClassPrepareEvent from the VM
    debugCache.actor !? createClassPrepareEvent(BaseName + "$b")

    assertTrue("Message was not received by listening actor before timeout", testActor.awaitLatch(500))

    // unregister listener, and check get no data

    debugCache.deregisterClassPrepareEventListener(testActor, BaseName)

    testActor.resetLatch

    debugCache.actor !? createClassPrepareEvent(BaseName + "$b")

    assertFalse("Unexpected message was received by listening actor before timeout", testActor.awaitLatch(500))

    verifyCalls(debugCache, classPrepareRequest1, classPrepareRequest2, BaseName)
  }
  
  private def toSortedListOfTypeName(types: Set[ReferenceType]): List[String] = {
    types.map(_.name()).toList.sorted
  }

  /** Create all the mocks required.
   *  
   *  @param initialTypeNames the name of the types to return for 'allClasses'
   */
  private def initMocks(initialTypeNames: String*): (ScalaDebugCache, ClassPrepareRequest, ClassPrepareRequest) = {
    val debugTarget = mock(classOf[ScalaDebugTarget])
    val virtualMachine = mock(classOf[VirtualMachine])
    when(debugTarget.virtualMachine).thenReturn(virtualMachine)

    import scala.collection.JavaConverters._
    val allClasses = initialTypeNames.map(createReferenceType(_)).asJava
    when(virtualMachine.allClasses()).thenReturn(allClasses)

    val eventDispatcher = mock(classOf[ScalaJdiEventDispatcher])
    when(debugTarget.eventDispatcher).thenReturn(eventDispatcher)

    val eventRequestManager = mock(classOf[EventRequestManager])
    when(virtualMachine.eventRequestManager()).thenReturn(eventRequestManager)

    val classPrepareRequest1 = mock(classOf[ClassPrepareRequest])
    val classPrepareRequest2 = mock(classOf[ClassPrepareRequest])
    when(eventRequestManager.createClassPrepareRequest()).thenReturn(classPrepareRequest1, classPrepareRequest2)
    (classPrepareRequest1, classPrepareRequest2)

    val debugTargetActor = new BaseDebuggerActor {
      override def behavior = new PartialFunction[Any, Unit] {
        def apply(v1: Any): Unit = {}
        def isDefinedAt(x: Any): Boolean = false
      }
    }
    debugTargetActor.start
    val debugCache = ScalaDebugCache(debugTarget, debugTargetActor)

    toShutdown += (() => { debugTargetActor ! PoisonPill })
    toShutdown += debugCache.dispose

    (debugCache, classPrepareRequest1, classPrepareRequest2)
  }

  /** Check that the expected calls where made, and not too often.
   */
  private def verifyCalls(debugCache: ScalaDebugCache, classPrepareRequest1: ClassPrepareRequest, classPrepareRequest2: ClassPrepareRequest, baseName: String) {
    val virtualMachine = debugCache.debugTarget.virtualMachine
    // allClasses should be called only once
    verify(virtualMachine, times(1)).allClasses()

    // 2 ClassPrepareRequest should have been created, enabled then disabled
    verify(virtualMachine.eventRequestManager, times(2)).createClassPrepareRequest()
    verify(classPrepareRequest1, times(1)).enable()
    verify(classPrepareRequest1, times(1)).addClassFilter(baseName)
    verify(classPrepareRequest2, times(1)).enable()
    verify(classPrepareRequest2, times(1)).addClassFilter(baseName + "$*")
    verify(debugCache.debugTarget.eventDispatcher, times(1)).setActorFor(debugCache.actor, classPrepareRequest1)
    verify(debugCache.debugTarget.eventDispatcher, times(1)).setActorFor(debugCache.actor, classPrepareRequest2)
  }

  private def createReferenceType(name: String): ReferenceType = {
    val referenceType = mock(classOf[ReferenceType])
    when(referenceType.name()).thenReturn(name)
    referenceType
  }

  private def createClassPrepareEvent(name: String): ClassPrepareEvent = {
    val event = mock(classOf[ClassPrepareEvent])
    val referenceType = createReferenceType(name)
    when(event.referenceType()).thenReturn(referenceType)
    event
  }

}