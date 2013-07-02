package scala.tools.eclipse.debug.model

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.tools.eclipse.debug.BaseDebuggerActor
import scala.tools.eclipse.debug.PoisonPill
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.request.ClassPrepareRequest
import com.sun.jdi.request.EventRequestManager
import scala.tools.eclipse.testsetup.SDTTestUtils

class ScalaDebugCacheTest {

  val toShutdown = scala.collection.mutable.ListBuffer[() => Unit]()

  @After
  def shutdownRegistered() {
    toShutdown.foreach(_())
  }

  /** Test the returned nested class when no data was cached.
   *  Check a few possible false-positive in the regex match.
   */
  @Test
  def getNestedTypesNotCached() {
    val BaseName = "test01.a.Test"

    val (debugCache, classPrepareRequest1, classPrepareRequest2) = initNestedTypesMocks(BaseName, BaseName + "$", BaseName + "$a$b", BaseName + "$Test", BaseName + "$Test2", "test01.a.TestTest", "test01.b.Test", "a." + BaseName)

    SDTTestUtils.waitUntil(1000)(debugCache.running)
    assertTrue("The debugger cache is not running", debugCache.running)

    val actual = debugCache.getLoadedNestedTypes(BaseName)

    assertEquals("Wrong set of loaded nested types", Seq(BaseName, BaseName + "$", BaseName + "$Test", BaseName + "$Test2", BaseName + "$a$b"), toSortedListOfTypeName(actual))

    verifyNestedTypesCalls(debugCache, classPrepareRequest1, classPrepareRequest2, BaseName)

  }

  /** Test the returned nested class when a new nested class has been loaded after an initial request filled in the cache.
   */
  @Test
  def getNestedTypesUpdateCache() {
    val BaseName = "test02.a.Test"

    val (debugCache, classPrepareRequest1, classPrepareRequest2) = initNestedTypesMocks(BaseName, BaseName + "$", BaseName + "$a")

    val actual = debugCache.getLoadedNestedTypes(BaseName)

    assertEquals("Wrong set of loaded nested types", Seq(BaseName, BaseName + "$", BaseName + "$a"), toSortedListOfTypeName(actual))

    // 'receive' a ClassPrepareEvent from the VM
    debugCache.actor !? createClassPrepareEvent(BaseName + "$b")

    val actual2 = debugCache.getLoadedNestedTypes(BaseName)

    assertEquals("Wrong set of loaded nested types", Seq(BaseName, BaseName + "$", BaseName + "$a", BaseName + "$b"), toSortedListOfTypeName(actual2))

    verifyNestedTypesCalls(debugCache, classPrepareRequest1, classPrepareRequest2, BaseName)
  }

  /** Test that ClassPrepareEvent events are sent when needed, and not when no requested.
   */
  @Test
  def addAndRemoveListener() {
    val BaseName = "test03.a.Test"

    val (debugCache, classPrepareRequest1, classPrepareRequest2) = initNestedTypesMocks(BaseName, BaseName + "$", BaseName + "$a")

    // a latch listener actor. It releases the latch when it receives a ClassPrepareEvent
    val testActor = new BaseDebuggerActor {
      @volatile var latch = new CountDownLatch(1)

      override def behavior = {
        case e: ClassPrepareEvent =>
          latch.countDown()
          reply(true)
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

    debugCache.addClassPrepareEventListener(testActor, BaseName)

    // 'receive' a ClassPrepareEvent from the VM
    debugCache.actor !? createClassPrepareEvent(BaseName + "$b")

    assertTrue("Message was not received by listening actor before timeout", testActor.awaitLatch(500))

    // unregister listener, and check get no data

    debugCache.removeClassPrepareEventListener(testActor, BaseName)

    testActor.resetLatch

    debugCache.actor !? createClassPrepareEvent(BaseName + "$b")

    assertFalse("Unexpected message was received by listening actor before timeout", testActor.awaitLatch(500))

    verifyNestedTypesCalls(debugCache, classPrepareRequest1, classPrepareRequest2, BaseName)
  }

  /** Checks that the information about existing anon function method is correctly cached.
   */
  @Test
  def getAnonFunctionSome() {
    val BaseName = "test04.a.Test"
    val MethodName = "applyOneMethod"

    val (debugCache, refType) = initAnonFunctionMocks(BaseName, MethodName)

    val actual = debugCache.getAnonFunction(refType)

    assertEquals("Wrong method", MethodName, actual.get.name())

    // and ask again, to check caching

    val actual2 = debugCache.getAnonFunction(refType)

    assertEquals("Wrong method", MethodName, actual2.get.name())

    verifyAnonFunctionCalls(refType)
  }

  /** Checks that the information about non-existing anon function method is correctly cached.
   */
  @Test
  def getAnonFunctionNone() {
    val BaseName = "test05.a.Test"
    val MethodName = "notApplyMethod"

    val (debugCache, refType) = initAnonFunctionMocks(BaseName, MethodName)

    val actual = debugCache.getAnonFunction(refType)

    assertEquals("Unexpected method", None, actual)

    // and ask again, to check caching

    val actual2 = debugCache.getAnonFunction(refType)

    assertEquals("Unexpected method", None, actual2)

    verifyAnonFunctionCalls(refType)
  }

  /** Checks that method flags with 'true' value are correctly cached.
   */
  @Test
  def methodFlagsAllTrue() {
    val BaseName = "test06.a.Test"
    val MethodName = "someMethod6"

    val (debugCache, method, location) = initMethodFlagsMocks(BaseName, MethodName)

    when(method.isConstructor()).thenReturn(true)
    when(method.isBridge()).thenReturn(true)

    assertTrue("Should be transparent", debugCache.isTransparentLocation(location))
    assertTrue("Should be opaque", debugCache.isOpaqueLocation(location))

    // and ask again, to check caching

    assertTrue("Should be transparent", debugCache.isTransparentLocation(location))
    assertTrue("Should be opaque", debugCache.isOpaqueLocation(location))

    verifyMethodFlagsCalls(method)
  }

  /** Checks that method flags with 'false' value are correctly cached.
   */
  @Test
  def methodFlagsAllFalse() {
    val BaseName = "test07.a.Test"
    val MethodName = "someMethod7"

    val (debugCache, method, location) = initMethodFlagsMocks(BaseName, MethodName)

    when(method.isConstructor()).thenReturn(false)
    when(method.isBridge()).thenReturn(false)

    assertFalse("Should be not transparent", debugCache.isTransparentLocation(location))
    assertFalse("Should be not opaque", debugCache.isOpaqueLocation(location))

    // and ask again, to check caching

    assertFalse("Should be not transparent", debugCache.isTransparentLocation(location))
    assertFalse("Should be not opaque", debugCache.isOpaqueLocation(location))

    verifyMethodFlagsCalls(method)
  }

  private def toSortedListOfTypeName(types: Set[ReferenceType]): List[String] = {
    types.map(_.name()).toList.sorted
  }

  /** Create all the mocks required for nested type cache tests.
   *
   *  @param initialTypeNames the name of the types to return for 'allClasses'
   */
  private def initNestedTypesMocks(initialTypeNames: String*): (ScalaDebugCache, ClassPrepareRequest, ClassPrepareRequest) = {
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
  private def verifyNestedTypesCalls(debugCache: ScalaDebugCache, classPrepareRequest1: ClassPrepareRequest, classPrepareRequest2: ClassPrepareRequest, baseName: String) {
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

  /** Create all the mocks required for anon function cache tests.
   */
  private def initAnonFunctionMocks(typeName: String, methodNames: String*): (ScalaDebugCache, ReferenceType) = {
    val debugTarget = mock(classOf[ScalaDebugTarget])

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

    val refType = createReferenceType(typeName)
    import scala.collection.JavaConverters._
    val methods = methodNames.map(createMethod(_)).asJava
    when(refType.methods()).thenReturn(methods)

    (debugCache, refType)

  }

  /** Check that the expected calls where made, and not too often.
   */
  private def verifyAnonFunctionCalls(refType: ReferenceType) {
    verify(refType, times(1)).methods()
  }

  /** Create all the mocks required for method flags cache tests.
   */
  private def initMethodFlagsMocks(typeName: String, methodName: String): (ScalaDebugCache, Method, Location) = {
    val debugTarget = mock(classOf[ScalaDebugTarget])

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

    val location = mock(classOf[Location])
    val method = createMethod(methodName)
    when(location.method()).thenReturn(method)
    val refType = createReferenceType(typeName)
    when(method.declaringType()).thenReturn(refType)
    val virtualMachine = mock(classOf[VirtualMachine])
    when(method.virtualMachine()).thenReturn(virtualMachine)
    when(virtualMachine.canGetBytecodes()).thenReturn(false)

    (debugCache, method, location)

  }

  /** Check that the expected calls where made, and not too often.
   */
  private def verifyMethodFlagsCalls(method: Method) {
    verify(method, times(1)).isBridge()
    verify(method, times(1)).isConstructor()
  }

  private def createReferenceType(name: String): ReferenceType = {
    val referenceType = mock(classOf[ReferenceType])
    when(referenceType.name()).thenReturn(name)
    referenceType
  }

  private def createMethod(name: String): Method = {
    val method = mock(classOf[Method])
    when(method.name()).thenReturn(name)
    method
  }

  private def createClassPrepareEvent(name: String): ClassPrepareEvent = {
    val event = mock(classOf[ClassPrepareEvent])
    val referenceType = createReferenceType(name)
    when(event.referenceType()).thenReturn(referenceType)
    event
  }

}