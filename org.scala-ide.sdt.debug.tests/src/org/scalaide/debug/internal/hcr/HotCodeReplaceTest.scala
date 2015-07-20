/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.hcr

import scala.collection.mutable.Publisher
import scala.collection.mutable.Subscriber
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NoStackTrace

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.model.IBreakpoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.debug.internal.ScalaDebugRunningTest
import org.scalaide.debug.internal.ScalaDebugTestSession
import org.scalaide.debug.internal.hcr.HotCodeReplaceTest.Matcher
import org.scalaide.debug.internal.preferences.HotCodeReplacePreferences

import HotCodeReplaceTest.ClassMethodBeginning
import HotCodeReplaceTest.ClassMethodEnd
import HotCodeReplaceTest.DefaultRecursiveMethodLocalIntValue
import HotCodeReplaceTest.IntFromCtorArgName
import HotCodeReplaceTest.JavaClassFilePath
import HotCodeReplaceTest.JavaClassMethodEndLine
import HotCodeReplaceTest.Location
import HotCodeReplaceTest.MainMethodBeginning
import HotCodeReplaceTest.MainMethodEnd
import HotCodeReplaceTest.Matcher
import HotCodeReplaceTest.RecursiveMethodBeginning
import HotCodeReplaceTest.RecursiveMethodEnd
import HotCodeReplaceTest.RecursiveMethodSelfCall
import HotCodeReplaceTest.RunMethodBeginning
import HotCodeReplaceTest.RunMethodEnd
import HotCodeReplaceTest.TestHcrSuccessListener
import HotCodeReplaceTest.TestedFilePath
import ScalaHotCodeReplaceManager.HCRResult
import ScalaHotCodeReplaceManager.HCRSucceeded

private object HotCodeReplaceTest {

  class Matcher[T](check: T => Unit) {
    def mustEqual(expected: T): Unit = check(expected)
  }

  case class Location(typeName: String, methodSignature: String, lineNumber: Int) {
    def asObsolete = Location(typeName, "Obsolete method", lineNumber = -1)
  }

  /**
   * Checks whether we got exactly one message and it's of type HCRSucceeded.
   * Otherwise, when checking whether HCR succeeded, the test fails with an appropriate message.
   */
  final class TestHcrSuccessListener extends Subscriber[HCRResult, Publisher[HCRResult]] {

    private var onlyOneSuccessReceived: Try[Boolean] = Success(false)

    def checkIfSucceeded: Boolean = onlyOneSuccessReceived match {
      case Success(value) => value
      case Failure(e) =>
        // New exception to have informative stack trace.
        throw new IllegalStateException("Unexpected message(s). Check the chained exception(s) for more information.", e)
    }

    override def notify(publisher: Publisher[HCRResult], event: HCRResult): Unit = event match {
      case msg: HCRSucceeded => handleSuccess(msg)
      case msg => handleWrongTypeOfMessage(msg)
    }

    private def handleSuccess(msg: HCRSucceeded): Unit = onlyOneSuccessReceived match {
      case Success(false) =>
        onlyOneSuccessReceived = Success(true)
      case Failure(prevThrowable) =>
        setFailureWithChainedThrowable(s"Received '$msg' but something went wrong previously.", prevThrowable)
      case _ =>
        setFailure("HCRSucceeded published twice.")
    }

    // We chain exceptions related to particular events for easier debugging.
    private def handleWrongTypeOfMessage(msg: HCRResult): Unit = onlyOneSuccessReceived match {
      case Success(false) =>
        setFailure(s"Received an unexpected message '$msg'. HCRSucceeded is expected.")
      case Failure(prevThrowable) =>
        setFailureWithChainedThrowable(s"Received an unexpected message '$msg'.", prevThrowable)
      case _ =>
        setFailure(s"Received an unexpected message '$msg' after receiving expected HCRSucceeded.")
    }

    private def setFailure(errorMsg: String): Unit =
      onlyOneSuccessReceived = Failure(new Exception(errorMsg) with NoStackTrace)

    private def setFailureWithChainedThrowable(errorMsg: String, previous: Throwable): Unit =
      onlyOneSuccessReceived = Failure(new Exception(errorMsg, previous) with NoStackTrace)
  }

  val MainObjectTypeName = "debug.MainObject$"
  val NestedClassTypeName = "debug.MainObject$nestedObject$NestedClass"
  val CustomThreadTypeName = "debug.MainObject$CustomThread$"
  val TestedFilePath = "debug/Hcr.scala"
  val JavaClassFilePath = "debug/JavaClass.java"
  val IntFromCtorArgName = "intFromCtor"

  val RecursiveMethodSignature = "recursiveMethod(I)I"
  val MainMethodSignature = "mainMethod()V"
  val ClassMethodSignature = "classMethod()I"
  val RunMethodSignature = "run()V"

  val ClassMethodBeginning = Location(NestedClassTypeName, ClassMethodSignature, 7)
  val ClassMethodEnd = ClassMethodBeginning.copy(lineNumber = 9)

  val RecursiveMethodBeginning = Location(MainObjectTypeName, RecursiveMethodSignature, 15)
  val RecursiveMethodSelfCall = RecursiveMethodBeginning.copy(lineNumber = 18)
  val RecursiveMethodEnd = RecursiveMethodBeginning.copy(lineNumber = 21)

  val MainMethodBeginning = Location(MainObjectTypeName, MainMethodSignature, 25)
  val MainMethodEnd = MainMethodBeginning.copy(lineNumber = 26)

  val RunMethodBeginning = Location(CustomThreadTypeName, RunMethodSignature, 33)
  val RunMethodEnd = RunMethodBeginning.copy(lineNumber = 34)

  val JavaClassMethodEndLine = 5
  val DefaultRecursiveMethodLocalIntValue = 105
}

/**
 * Tests whether HCR works (classes are correctly replaced in VM and we get new values)
 * and whether associated settings are correctly applied.
 */
class HotCodeReplaceTest
    extends TestProjectSetup("hot-code-replace", bundleName = "org.scala-ide.sdt.debug.tests")
    with ScalaDebugRunningTest {

  import HotCodeReplaceTest._

  private var session: ScalaDebugTestSession = _
  private var breakpoints: List[IBreakpoint] = Nil

  // hcr-related tests modify sources so for each test we recreate test workspace in original state
  @Before
  def initializeTests(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
    session = initDebugSession("hcr")
    HotCodeReplacePreferences.hcrEnabled = true
    HotCodeReplacePreferences.dropObsoleteFramesAutomatically = true
  }

  @After
  def cleanDebugSession(): Unit = {
    breakpoints foreach session.removeBreakpoint
    session.terminate()
    session = null
    SDTTestUtils.deleteProjects(project)
  }

  private def initDebugSession(launchConfigurationName: String) =
    ScalaDebugTestSession(file(launchConfigurationName + ".launch"))

  private val recursiveMethodLocalInt = intValueMatcher("recursiveMethodLocalInt")
  private val remainingRecursiveCallsCounter = intValueMatcher("remainingRecursiveCallsCounter")
  private val classLocalInt = intValueMatcher("classLocalInt")
  private val classLocalIntReceivedFromJava = intValueMatcher("classLocalIntReceivedFromJava")

  private def intValueMatcher(valName: String): Matcher[Int] = {
    def expectIntValue(expectedValue: Int): Unit = {
      val actualValue = session.getLocalVariable(valName).getValueString().toInt
      assertEquals(s"Wrong value of $valName", expectedValue, actualValue)
    }
    new Matcher(expectIntValue)
  }

  private val currentFrameLocation: Matcher[Location] = {
    def expectCurrentFrameLocation(expectedLocation: Location): Unit =
      session.checkStackFrame(expectedLocation.typeName, expectedLocation.methodSignature, expectedLocation.lineNumber)

    new Matcher(expectCurrentFrameLocation)
  }

  private def dropToTopFrame(): Unit = session dropToFrame session.currentStackFrame

  private def addLineBreakpointAt(location: Location): Unit =
    breakpoints = breakpoints :+ session.addLineBreakpoint(location.typeName, location.lineNumber)

  private def buildWithModifiedLineAndHcr(lineNumber: Int, newLine: String): Unit = {
    modifyLine(compilationUnitPath = TestedFilePath, lineNumber, newLine)
    buildAndEnsureHcrSucceeded()
  }

  private def buildWithNewValueAssignedToClassLocalInt(newValue: Int): Unit =
    buildWithModifiedLineAndHcr(ClassMethodBeginning.lineNumber, s"val classLocalInt = $newValue")

  private def buildWithNewValueReturnedFromClassMethod(newLine: String): Unit =
    buildWithModifiedLineAndHcr(ClassMethodEnd.lineNumber, newLine)

  private def buildWithNewParamOfNestedClassFactoryMethod(paramValue: Int): Unit =
    buildWithModifiedLineAndHcr(RecursiveMethodBeginning.lineNumber, s"val instance = nestedObject.NestedClass($paramValue)")

  private def buildWithNewValueAssignedToCustomThreadLocalString(newValue: String): Unit =
    buildWithModifiedLineAndHcr(RunMethodBeginning.lineNumber, s"""val customThreadLocalString = "$newValue"""")

  private def buildWithNewParamOfRecursiveMethod(newValue: Int): Unit =
    buildWithModifiedLineAndHcr(MainMethodEnd.lineNumber, s"recursiveMethod($newValue)")

  private def updateValueReturnedByJavaClassMethod(newLine: String): Unit =
    modifyLine(compilationUnitPath = JavaClassFilePath, JavaClassMethodEndLine, newLine)

  private def buildAndEnsureHcrSucceeded(): Unit = {
    // 20 seconds should be enough even for Jenkins builds running under high load.
    // For comparison, it seems that in normal situation even about 100-200 millis can be enough.
    val hcrFinishedTimeoutMillis = 20000

    val hcrEventsSubscriber = new TestHcrSuccessListener
    val hcrEventsPublisher = session.debugTarget.subordinate.asInstanceOf[Publisher[HCRResult]]
    hcrEventsPublisher.subscribe(hcrEventsSubscriber)

    val thread = session.currentStackFrame.thread
    def isExpectedEvent(e: DebugEvent) = e.getSource == thread &&
      e.getKind == DebugEvent.CHANGE && e.getDetail == DebugEvent.CONTENT
    var threadContentChangedEventReceived = false

    // An additional check - an event fired after refreshing frames in ScalaThread.
    // Note: It's not impossible that some day there will be another such an event but sent before we'll
    // send this real expected one. Then we would state too early that it's done.
    val debugEventListener = ScalaDebugTestSession.addDebugEventListener {
      case e: DebugEvent if isExpectedEvent(e) => threadContentChangedEventReceived = true
    }

    // If we performed automatic dropping frames, we have to be sure that
    // it finished and the test session updated its state.
    def testSessionIsSuspended = session.state == session.State.SUSPENDED

    buildIncrementally()
    try {
      SDTTestUtils.waitUntil(hcrFinishedTimeoutMillis, withTimeoutException = true) {
        hcrEventsSubscriber.checkIfSucceeded &&
          threadContentChangedEventReceived &&
          testSessionIsSuspended
      }
    } finally {
      hcrEventsPublisher.removeSubscription(hcrEventsSubscriber)
      ScalaDebugTestSession.removeDebugEventListener(debugEventListener)
    }
  }

  private def createAndGoToBreakpointAtTheEndOfRecursiveMethod(): Unit = {
    // GIVEN the thread is suspended at the correct breakpoint and we're sure initial values are correct
    addLineBreakpointAt(RecursiveMethodEnd)
    session.launch()
    session.waitUntilSuspended()
    currentFrameLocation mustEqual RecursiveMethodEnd
    remainingRecursiveCallsCounter mustEqual 0
    recursiveMethodLocalInt mustEqual DefaultRecursiveMethodLocalIntValue
  }

  private def createAndGoToBreakpointAtTheEndOfClassMethod(): Unit = {
    // GIVEN the thread is suspended at the correct breakpoint and we're sure initial value is correct
    addLineBreakpointAt(ClassMethodEnd)
    session.launch()
    session.waitUntilSuspended()
    currentFrameLocation mustEqual ClassMethodEnd
    classLocalInt mustEqual 7
    classLocalIntReceivedFromJava mustEqual -20
  }

  @Test
  def successfulHcrWithSimpleMethod(): Unit = {
    createAndGoToBreakpointAtTheEndOfClassMethod()

    // WHEN edit code and rebuild
    buildWithNewValueAssignedToClassLocalInt(8)

    // THEN we automatically returned to the beginning of a method
    currentFrameLocation mustEqual ClassMethodBeginning

    // WHEN resume execution
    session.resumeToSuspension()

    // THEN we stopped at the breakpoint, new value is applied
    currentFrameLocation mustEqual ClassMethodEnd
    classLocalInt mustEqual 8

    // WHEN edit code once again and rebuild
    buildWithNewValueAssignedToClassLocalInt(10)

    // THEN we automatically returned to the beginning of a method
    currentFrameLocation mustEqual ClassMethodBeginning

    // WHEN resume execution
    session.resumeToSuspension()

    // THEN we stopped at the breakpoint, new value is applied
    currentFrameLocation mustEqual ClassMethodEnd
    classLocalInt mustEqual 10
  }

  @Test
  def hcrWithDisabledAutomaticDroppingFrames(): Unit = {
    HotCodeReplacePreferences.dropObsoleteFramesAutomatically = false
    HotCodeReplacePreferences.allowToDropObsoleteFramesManually = true

    createAndGoToBreakpointAtTheEndOfClassMethod()

    // WHEN edit code and rebuild
    buildWithNewValueAssignedToClassLocalInt(8)

    // THEN current frame is obsolete
    currentFrameLocation mustEqual ClassMethodEnd.asObsolete

    // WHEN drop one frame and resume
    dropToTopFrame()
    session.resumeToSuspension()

    // THEN thread is suspended at the same breakpoint and new values are applied
    currentFrameLocation mustEqual ClassMethodEnd
    classLocalInt mustEqual 8
  }

  @Test
  def successfulHcrWithMethodNotInStackTrace(): Unit = {
    createAndGoToBreakpointAtTheEndOfRecursiveMethod()

    // WHEN edit code and rebuild
    buildWithNewValueReturnedFromClassMethod(s"230 + $IntFromCtorArgName")

    // AND return to the beginning of a current method
    dropToTopFrame()
    currentFrameLocation mustEqual RecursiveMethodBeginning

    // AND recompute current frame
    session.resumeToSuspension()

    // THEN final values are correct
    currentFrameLocation mustEqual RecursiveMethodEnd
    remainingRecursiveCallsCounter mustEqual 0
    recursiveMethodLocalInt mustEqual 235
  }

  @Ignore("Probably the VM crash will be fixed, when the automatic semantic dropping frames BEFORE HCR will be implemented")
  @Test
  def successfulHcrWithRecursiveMethod(): Unit = {
    createAndGoToBreakpointAtTheEndOfRecursiveMethod()

    // WHEN edit code and rebuild
    buildWithNewParamOfNestedClassFactoryMethod(50)

    // THEN we automatically returned to the beginning of a method and dropped all old frames
    // related to this method from the recursive call
    currentFrameLocation mustEqual RecursiveMethodBeginning
    remainingRecursiveCallsCounter mustEqual 2

    // WHEN resume execution
    session.resumeToSuspension()

    // THEN we stopped at the breakpoint, new values are applied
    currentFrameLocation mustEqual RecursiveMethodEnd
    remainingRecursiveCallsCounter mustEqual 0
    recursiveMethodLocalInt mustEqual 150
  }

  @Test
  def oneClassNotReplacedDueToErrorsInCode(): Unit = {
    HotCodeReplacePreferences.performHcrForFilesContainingErrors = false
    createAndGoToBreakpointAtTheEndOfClassMethod()

    // WHEN edit code both in Scala and Java source files and build, Java source file contains error
    updateValueReturnedByJavaClassMethod("return 8-/;")
    buildWithNewValueAssignedToClassLocalInt(8)

    // THEN we automatically returned to the beginning of a method
    currentFrameLocation mustEqual ClassMethodBeginning

    // WHEN resume execution
    session.resumeToSuspension()

    // THEN we stopped at the breakpoint, only value changed in Scala file is updated
    currentFrameLocation mustEqual ClassMethodEnd
    classLocalIntReceivedFromJava mustEqual -20
    classLocalInt mustEqual 8

    // WHEN the error is corrected and classes rebuilt
    updateValueReturnedByJavaClassMethod("return 50;")
    buildAndEnsureHcrSucceeded()

    // AND drop one frame and resume
    dropToTopFrame()
    session.resumeToSuspension()

    // THEN we stopped at the breakpoint, value from Java is updated
    currentFrameLocation mustEqual ClassMethodEnd
    classLocalIntReceivedFromJava mustEqual 50
  }

  @Test
  def successfulHcrWithAffectedFartherFrame(): Unit = {
    createAndGoToBreakpointAtTheEndOfRecursiveMethod()

    addLineBreakpointAt(RecursiveMethodSelfCall)

    // WHEN edit code and rebuild
    buildWithNewParamOfRecursiveMethod(4)

    // THEN we automatically returned to the beginning of a method
    currentFrameLocation mustEqual MainMethodBeginning

    // WHEN resume execution
    session.resumeToSuspension()

    // THEN final values are correct
    currentFrameLocation mustEqual RecursiveMethodSelfCall
    remainingRecursiveCallsCounter mustEqual 4
    recursiveMethodLocalInt mustEqual DefaultRecursiveMethodLocalIntValue
  }

  @Test
  def disabledHcr(): Unit = {
    HotCodeReplacePreferences.hcrEnabled = false
    createAndGoToBreakpointAtTheEndOfRecursiveMethod()

    // THEN there's no ScalaHotCodeReplaceManager created for debug session
    assertEquals("hcrManager shouldn't be created", None, session.debugTarget.hcrManager)
  }

  // Sometimes after a few HCR operations VM doesn't mark frames as obsolete. Then we don't perform
  // Drop To Frame what is misleading. Then user have to run it manually and everything starts working
  // as expected. Anyway it should get better when we'll stop using isObsolete to state what should be dropped.
  @Ignore("Hopefully it will be fixed, when the automatic semantic dropping frames BEFORE HCR will be implemented")
  @Test
  def automaticDroppingFramesAfterManyHcrOperationsInARow(): Unit = {
    createAndGoToBreakpointAtTheEndOfClassMethod()

    // WHEN edit code and rebuild
    buildWithNewValueAssignedToClassLocalInt(10)

    // THEN we automatically returned to the beginning of a method
    currentFrameLocation mustEqual ClassMethodBeginning

    // WHEN edit code and rebuild once again without resuming in meantime
    buildWithNewParamOfNestedClassFactoryMethod(50)

    // THEN we automatically returned to the beginning of a method containing the last change
    currentFrameLocation mustEqual RecursiveMethodBeginning
    // WHEN resume execution
    session.resumeToSuspension()

    // THEN we stopped at the breakpoint, new value is applied
    currentFrameLocation mustEqual ClassMethodEnd
    classLocalInt mustEqual 10
  }

  @Test
  def doNotDropLastFrame(): Unit = {
    // GIVEN the thread is suspended at the correct breakpoint in the separate thread and initial values are correct
    addLineBreakpointAt(RunMethodEnd)
    session.launch()
    session.waitUntilSuspended()
    currentFrameLocation mustEqual RunMethodEnd

    // WHEN edit code and rebuild
    buildWithNewValueAssignedToCustomThreadLocalString("other string")

    // THEN current frame is obsolete - we couldn't drop the only stack frame
    currentFrameLocation mustEqual RunMethodEnd.asObsolete
  }

  @Test
  def prohibitedDroppingObsoleteFramesManuallyDoesNotAffectAutomaticDropping(): Unit = {
    HotCodeReplacePreferences.allowToDropObsoleteFramesManually = false
    createAndGoToBreakpointAtTheEndOfClassMethod()

    // WHEN edit code and rebuild
    buildWithNewValueAssignedToClassLocalInt(8)

    // THEN we automatically returned to the beginning despite set flag
    currentFrameLocation mustEqual ClassMethodBeginning
  }
}
