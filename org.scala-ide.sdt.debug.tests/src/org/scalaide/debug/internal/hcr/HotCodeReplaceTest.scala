/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.hcr

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
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
import org.scalaide.debug.internal.preferences.HotCodeReplacePreferences
import org.scalaide.core.FlakyTest

private object HotCodeReplaceTest {

  class Matcher[T](check: T => Unit) {
    def mustEqual(expected: T): Unit = check(expected)
  }

  case class Location(typeName: String, methodSignature: String, lineNumber: Int) {
    def asObsolete = Location(typeName, "Obsolete method", lineNumber = -1)
  }

  val MainObjectTypeName = "debug.MainObject$"
  val NestedClassTypeName = "debug.MainObject$nestedObject$NestedClass"
  val CustomThreadTypeName = "debug.MainObject$CustomThread$"
  val TestedFilePath = "debug/Hcr.scala"
  val IntFromCtorArgName = "intFromCtor"

  val RecursiveMethodSignature = "recursiveMethod(I)I"
  val MainMethodSignature = "mainMethod()V"
  val ClassMethodSignature = "classMethod()I"
  val RunMethodSignature = "run()V"

  val ClassMethodBeginning = Location(NestedClassTypeName, ClassMethodSignature, 7)
  val ClassMethodEnd = ClassMethodBeginning.copy(lineNumber = 8)

  val RecursiveMethodBeginning = Location(MainObjectTypeName, RecursiveMethodSignature, 14)
  val RecursiveMethodSelfCall = RecursiveMethodBeginning.copy(lineNumber = 17)
  val RecursiveMethodEnd = RecursiveMethodBeginning.copy(lineNumber = 20)

  val MainMethodBeginning = Location(MainObjectTypeName, MainMethodSignature, 24)
  val MainMethodEnd = MainMethodBeginning.copy(lineNumber = 25)

  val RunMethodBeginning = Location(CustomThreadTypeName, RunMethodSignature, 32)
  val RunMethodEnd = RunMethodBeginning.copy(lineNumber = 33)

  val DefaultRecursiveMethodLocalIntValue = 105
}

/**
 * Tests whether HCR works (classes are correctly replaced in VM and we get new values)
 * and whether associated settings are correctly applied.
 */
@Ignore("Flaky, often fails in Scala PR validation.")
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
  private def dropToFrameWithIndex(frameIndex: Int): Unit = session dropToFrame session.currentStackFrames(frameIndex)

  private def addLineBreakpointAt(location: Location): Unit =
    breakpoints = breakpoints :+ session.addLineBreakpoint(location.typeName, location.lineNumber)

  private def disableHcr(): Unit =
    HotCodeReplacePreferences.hcrEnabled = false

  private def disableHcrForFilesContainingErrors(): Unit =
    HotCodeReplacePreferences.performHcrForFilesContainingErrors = false

  private def buildWithNewValueAssignedToClassLocalInt(newValue: Int): Unit =
    buildWithModifiedLine(TestedFilePath, ClassMethodBeginning.lineNumber, s"val classLocalInt = $newValue")

  private def buildWithNewValueReturnedFromClassMethod(newLine: String): Unit =
    buildWithModifiedLine(TestedFilePath, ClassMethodEnd.lineNumber, newLine)

  private def buildWithNewParamOfNestedClassFactoryMethod(paramValue: Int): Unit =
    buildWithModifiedLine(TestedFilePath,
      RecursiveMethodBeginning.lineNumber,
      s"val instance = nestedObject.NestedClass($paramValue)")

  private def buildWithNewValueAssignedToCustomThreadLocalString(newValue: String): Unit =
    buildWithModifiedLine(TestedFilePath,
      RunMethodBeginning.lineNumber,
      s"""val customThreadLocalString = "$newValue"""")

  private def buildWithNewParamOfRecursiveMethod(newValue: Int): Unit =
    buildWithModifiedLine(TestedFilePath, MainMethodEnd.lineNumber, s"recursiveMethod($newValue)")

  private def buildWithIncorrectParamOfRecursiveMethod(newValue: String): Unit =
    buildWithModifiedLine(TestedFilePath, MainMethodEnd.lineNumber, s"""recursiveMethod("$newValue")""")

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
  }

  @Test
  def successfulHcrWithSimpleMethod(): Unit = FlakyTest.retry("successfulHcrWithSimpleMethod") {
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
  def hcrWithDisabledAutomaticDroppingFrames(): Unit = FlakyTest.retry("hcrWithDisabledAutomaticDroppingFrames") {
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
  def successfulHcrWithMethodNotInStackTrace(): Unit = FlakyTest.retry("successfulHcrWithMethodNotInStackTrace") {
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

  @Test
  def disabledHcrWithMethodNotInStackTrace(): Unit = FlakyTest.retry("disabledHcrWithMethodNotInStackTrace") {
    disableHcr()
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
    recursiveMethodLocalInt mustEqual DefaultRecursiveMethodLocalIntValue
  }

  @Test
  def classesNotReplacedDueToErrorsInCodeWithMethodNotInStackTrace(): Unit = FlakyTest.retry("classesNotReplacedDueToErrorsInCodeWithMethodNotInStackTrace") {
    disableHcrForFilesContainingErrors()
    createAndGoToBreakpointAtTheEndOfRecursiveMethod()

    // WHEN edit code and rebuild
    buildWithNewValueReturnedFromClassMethod(s"2015 + compilation error!")

    // AND return to the beginning of a current method
    dropToTopFrame()
    currentFrameLocation mustEqual RecursiveMethodBeginning

    // AND recompute current frame
    session.resumeToSuspension()

    // THEN final values are correct
    currentFrameLocation mustEqual RecursiveMethodEnd
    remainingRecursiveCallsCounter mustEqual 0
    recursiveMethodLocalInt mustEqual DefaultRecursiveMethodLocalIntValue
  }

  @Ignore("Probably the VM crash will be fixed, when the automatic semantic dropping frames BEFORE HCR will be implemented")
  @Test
  def successfulHcrWithRecursiveMethod(): Unit = FlakyTest.retry("successfulHcrWithRecursiveMethod") {
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
  def successfulHcrWithAffectedFartherFrame(): Unit = FlakyTest.retry("successfulHcrWithAffectedFartherFrame") {
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
  def disabledHcr(): Unit = FlakyTest.retry("disabledHcr") {
    disableHcr()
    createAndGoToBreakpointAtTheEndOfRecursiveMethod()

    addLineBreakpointAt(RecursiveMethodSelfCall)

    // WHEN edit code and rebuild with disabled HCR
    buildWithNewParamOfRecursiveMethod(4)

    // THEN classes are not replaced
    currentFrameLocation mustEqual RecursiveMethodEnd
    dropToFrameWithIndex(3)

    currentFrameLocation mustEqual MainMethodBeginning

    session.resumeToSuspension()

    currentFrameLocation mustEqual RecursiveMethodSelfCall
    remainingRecursiveCallsCounter mustEqual 2
    recursiveMethodLocalInt mustEqual DefaultRecursiveMethodLocalIntValue
  }

  @Test
  def classesNotReplacedDueToErrorsInCode(): Unit = FlakyTest.retry("classesNotReplacedDueToErrorsInCode") {
    disableHcrForFilesContainingErrors()
    createAndGoToBreakpointAtTheEndOfRecursiveMethod()

    addLineBreakpointAt(RecursiveMethodSelfCall)

    // WHEN edit code and rebuild with errors
    buildWithIncorrectParamOfRecursiveMethod("compilation error as it's not Int")

    // THEN classes are not replaced
    currentFrameLocation mustEqual RecursiveMethodEnd
    dropToFrameWithIndex(3)

    currentFrameLocation mustEqual MainMethodBeginning

    session.resumeToSuspension()

    currentFrameLocation mustEqual RecursiveMethodSelfCall
    remainingRecursiveCallsCounter mustEqual 2
    recursiveMethodLocalInt mustEqual DefaultRecursiveMethodLocalIntValue
  }

  // Sometimes after a few HCR operations VM doesn't mark frames as obsolete. Then we don't perform
  // Drop To Frame what is misleading. Then user have to run it manually and everything starts working
  // as expected. Anyway it should get better when we'll stop using isObsolete to state what should be dropped.
  @Ignore("Hopefully it will be fixed, when the automatic semantic dropping frames BEFORE HCR will be implemented")
  @Test
  def automaticDroppingFramesAfterManyHcrOperationsInARow(): Unit = FlakyTest.retry("automaticDroppingFramesAfterManyHcrOperationsInARow") {
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
  def doNotDropLastFrame(): Unit = FlakyTest.retry("doNotDropLastFrame") {
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
  def prohibitedDroppingObsoleteFramesManuallyDoesNotAffectAutomaticDropping(): Unit = FlakyTest.retry("prohibitedDroppingObsoleteFramesManuallyDoesNotAffectAutomaticDropping") {
    HotCodeReplacePreferences.allowToDropObsoleteFramesManually = false
    createAndGoToBreakpointAtTheEndOfClassMethod()

    // WHEN edit code and rebuild
    buildWithNewValueAssignedToClassLocalInt(8)

    // THEN we automatically returned to the beginning despite set flag
    currentFrameLocation mustEqual ClassMethodBeginning
  }
}
