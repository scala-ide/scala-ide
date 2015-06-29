package org.scalaide.debug.internal

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert._
import org.junit.Before
import org.junit.Test
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.TestProjectSetup

object ScalaDebugSteppingTest extends TestProjectSetup("debug", bundleName = "org.scala-ide.sdt.debug.tests") with ScalaDebugRunningTest {

  var initialized = false

  def initDebugSession(launchConfigurationName: String): ScalaDebugTestSession = ScalaDebugTestSession(file(launchConfigurationName + ".launch"))

  @AfterClass
  def deleteProject(): Unit = {
    SDTTestUtils.deleteProjects(project)
  }
}

class ScalaDebugSteppingTest {

  import ScalaDebugSteppingTest._

  var session: ScalaDebugTestSession = null

  @Before
  def initializeTests(): Unit = {
    if (!initialized) {
      project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
      project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
      initialized = true
    }
  }

  @After
  def cleanDebugSession(): Unit = {
    if (session ne null) {
      session.terminate()
      session = null
    }
  }

  /*
   * Testing step over/in for comprehension through List[String]
   */

  @Test
  def StepOverIntoForComprehensionListStringInObjectMain(): Unit = {

    session = initDebugSession("ForComprehensionListString")

    session.runToLine(TYPENAME_FC_LS + "$", 9)

    session.checkStackFrame(TYPENAME_FC_LS + "$", "main([Ljava/lang/String;)V", 9)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$main$1", "apply(Ljava/lang/String;)I", 10)

    session.checkThreadsState
  }

  @Test
  def StepOverIntoForComprehensionListStringInObjectFoo(): Unit = {

    session = initDebugSession("ForComprehensionListString")

    session.runToLine(TYPENAME_FC_LS + "$", 19)

    session.checkStackFrame(TYPENAME_FC_LS + "$", "foo(Lscala/collection/immutable/List;)V", 19)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$foo$1", "apply(Ljava/lang/String;)I", 20)
  }

  @Test
  def StepOverIntoForComprehensionListStringInClassConstructor(): Unit = {

    session = initDebugSession("ForComprehensionListString")

    session.runToLine(TYPENAME_FC_LS, 29)

    session.checkStackFrame(TYPENAME_FC_LS, "<init>(Lscala/collection/immutable/List;)V", 29)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$1", "apply(Ljava/lang/String;)I", 30)
  }

  @Test
  def StepOverIntoForComprehensionListStringInClassBar(): Unit = {

    session = initDebugSession("ForComprehensionListString")

    session.runToLine(TYPENAME_FC_LS, 35)

    session.checkStackFrame(TYPENAME_FC_LS, "bar()V", 35)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$bar$1", "apply(Ljava/lang/String;)I", 36)
  }

  /*
   * Testing step over/back in for comprehension through List[String]
   */

  @Test
  def StepOverBackInForComprehentionListString(): Unit = {

    session = initDebugSession("ForComprehensionListString")

    session.runToLine(TYPENAME_FC_LS + "$", 10)

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$main$1", "apply(Ljava/lang/String;)I", 10)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$main$1", "apply(Ljava/lang/String;)I", 10)
  }

  /*
   * Testing step over/out for comprehension through List[String]
   */

  @Test
  def StepOverOutForComprehentionListString(): Unit = {

    session = initDebugSession("ForComprehensionListString2")

    session.runToLine(TYPENAME_FC_LS2 + "$", 12)

    session.checkStackFrame(TYPENAME_FC_LS2 + "$$anonfun$main$1", "apply(Ljava/lang/String;)I", 12)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS2 + "$", "main([Ljava/lang/String;)V", 15)
  }

  /*
   * Testing step over/in for comprehension through List[Object]
   */

  @Test
  def StepOverIntoForComprehensionListObjectInObjectMain(): Unit = {

    session = initDebugSession("ForComprehensionListObject")

    session.runToLine(TYPENAME_FC_LO + "$", 9)

    session.checkStackFrame(TYPENAME_FC_LO + "$", "main([Ljava/lang/String;)V", 9)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LO + "$$anonfun$main$1", "apply(Ljava/lang/Object;)Ljava/lang/Object;", 10)
  }

  @Test
  def StepOverIntoForComprehensionListObjectInObjectFoo(): Unit = {

    session = initDebugSession("ForComprehensionListObject")

    session.runToLine(TYPENAME_FC_LO + "$", 19)

    session.checkStackFrame(TYPENAME_FC_LO + "$", "foo(Lscala/collection/immutable/List;)V", 19)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LO + "$$anonfun$foo$1", "apply(Ljava/lang/Object;)Ljava/lang/Object;", 20)
  }

  @Test
  def StepOverIntoForComprehensionListObjectInClassConstructor(): Unit = {

    session = initDebugSession("ForComprehensionListObject")

    session.runToLine(TYPENAME_FC_LO, 29)

    session.checkStackFrame(TYPENAME_FC_LO, "<init>(Lscala/collection/immutable/List;)V", 29)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LO + "$$anonfun$1", "apply(Ljava/lang/Object;)Ljava/lang/Object;", 30)
  }

  @Test
  def StepOverIntoForComprehensionListObjectInClassBar(): Unit = {

    session = initDebugSession("ForComprehensionListObject")

    session.runToLine(TYPENAME_FC_LO, 35)

    session.checkStackFrame(TYPENAME_FC_LO, "bar()V", 35)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LO + "$$anonfun$bar$1", "apply(Ljava/lang/Object;)Ljava/lang/Object;", 36)
  }

  /*
   * Testing step over/in for comprehension through List[Int]
   * This tests are disable due to the changes and problem reported in SI-5646
   */

  @Test
  def StepOverIntoForComprehensionListIntInObjectMain(): Unit = {

    session = initDebugSession("ForComprehensionListInt")

    session.runToLine(TYPENAME_FC_LI + "$", 11)

    session.checkStackFrame(TYPENAME_FC_LI + "$", "main([Ljava/lang/String;)V", 11)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LI + "$$anonfun$main$1", "apply$mcVI$sp(I)V", 12)
  }

  @Test
  def StepOverIntoForComprehensionListIntInObjectFoo(): Unit = {

    session = initDebugSession("ForComprehensionListInt")

    session.runToLine(TYPENAME_FC_LI + "$", 22)

    session.checkStackFrame(TYPENAME_FC_LI + "$", "foo(Lscala/collection/immutable/List;)V", 22)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LI + "$$anonfun$foo$1", "apply$mcVI$sp(I)V", 23)
  }

  @Test
  def StepOverIntoForComprehensionListIntInClassConstructor(): Unit = {

    session = initDebugSession("ForComprehensionListInt")

    session.runToLine(TYPENAME_FC_LI, 33)

    session.checkStackFrame(TYPENAME_FC_LI, "<init>(Lscala/collection/immutable/List;)V", 33)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LI + "$$anonfun$1", "apply$mcVI$sp(I)V", 34)
  }

  @Test
  def StepOverIntoForComprehensionListIntInClassBar(): Unit = {

    session = initDebugSession("ForComprehensionListInt")

    session.runToLine(TYPENAME_FC_LI, 40)

    session.checkStackFrame(TYPENAME_FC_LI, "bar()V", 40)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LI + "$$anonfun$bar$1", "apply$mcVI$sp(I)V", 41)
  }

  @Test
  def StepOverIntoForComprehensionListIntInObjectMainOptimized(): Unit = {

    session = initDebugSession("ForComprehensionListIntOptimized")

    session.runToLine(TYPENAME_FC_LIO + "$", 11)

    session.checkStackFrame(TYPENAME_FC_LIO + "$", "main([Ljava/lang/String;)V", 11)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LIO + "$$anonfun$main$1", "apply$mcVI$sp(I)V", 12)
  }

  @Test
  def StepOverIntoForComprehensionListIntInObjectFooOptimized(): Unit = {

    session = initDebugSession("ForComprehensionListIntOptimized")

    session.runToLine(TYPENAME_FC_LIO + "$", 21)

    session.checkStackFrame(TYPENAME_FC_LIO + "$", "foo(Lscala/collection/immutable/List;)V", 21)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LIO + "$$anonfun$foo$1", "apply$mcVI$sp(I)V", 22)
  }

  @Test
  def StepOverIntoForComprehensionListIntInClassConstructorOptimized(): Unit = {

    session = initDebugSession("ForComprehensionListIntOptimized")

    session.runToLine(TYPENAME_FC_LIO, 31)

    session.checkStackFrame(TYPENAME_FC_LIO, "<init>(Lscala/collection/immutable/List;)V", 31)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LIO + "$$anonfun$1", "apply$mcVI$sp(I)V", 32)
  }

  @Test
  def StepOverIntoForComprehensionListIntInClassBarOptimized(): Unit = {

    session = initDebugSession("ForComprehensionListIntOptimized")

    session.runToLine(TYPENAME_FC_LIO, 37)

    session.checkStackFrame(TYPENAME_FC_LIO, "bar()V", 37)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LIO + "$$anonfun$bar$1", "apply$mcVI$sp(I)V", 38)
  }

  /*
   * Testing step over/in List[String] methods
   */

  @Test
  def StepOverIntoListStringForEach(): Unit = {

    session = initDebugSession("AnonFunOnListString")

    session.runToLine(TYPENAME_AF_LS + "$", 19)

    session.checkStackFrame(TYPENAME_AF_LS + "$", "a(Lscala/collection/immutable/List;)V", 19)

    session.stepOver()

    session.checkStackFrame(TYPENAME_AF_LS + "$$anonfun$a$1", "apply(Ljava/lang/String;)V", 19)
  }

  @Test
  def StepOverIntoListStringFind(): Unit = {

    session = initDebugSession("AnonFunOnListString")

    session.runToLine(TYPENAME_AF_LS + "$", 25)

    session.checkStackFrame(TYPENAME_AF_LS + "$", "b(Lscala/collection/immutable/List;)V", 25)

    session.stepOver()

    session.checkStackFrame(TYPENAME_AF_LS + "$$anonfun$b$1", "apply(Ljava/lang/String;)Z", 25)
  }

  @Test
  def StepOverIntoListStringMap(): Unit = {

    session = initDebugSession("AnonFunOnListString")

    session.runToLine(TYPENAME_AF_LS + "$", 31)

    session.checkStackFrame(TYPENAME_AF_LS + "$", "c(Lscala/collection/immutable/List;)V", 31)

    session.stepOver()

    session.checkStackFrame(TYPENAME_AF_LS + "$$anonfun$c$1", "apply(Ljava/lang/String;)I", 31)
  }

  @Test
  def StepOverIntoListStringFoldLeft(): Unit = {

    session = initDebugSession("AnonFunOnListString")

    session.runToLine(TYPENAME_AF_LS + "$", 37)

    session.checkStackFrame(TYPENAME_AF_LS + "$", "d(Lscala/collection/immutable/List;)V", 37)

    session.stepOver()

    session.checkStackFrame(TYPENAME_AF_LS + "$$anonfun$d$1", "apply(ILjava/lang/String;)I", 37)
  }

  // Simple stepping into/over/out tests

  @Test
  def StepIntoSimpleTest(): Unit = {
    session = initDebugSession("SimpleStepping")

    session.runToLine(TYPENAME_SIMPLE_STEPPING, 8)

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "foo()V", 8)

    session.stepInto()

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "bar()V", 12)
  }

  @Test
  def StepOverSimpleTest(): Unit = {
    session = initDebugSession("SimpleStepping")

    session.runToLine(TYPENAME_SIMPLE_STEPPING, 12)

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "bar()V", 12)

    session.stepOver()

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "bar()V", 13)
  }

  @Test
  def StepReturnSimpleTest(): Unit = {
    session = initDebugSession("SimpleStepping")

    session.runToLine(TYPENAME_SIMPLE_STEPPING, 12)

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "bar()V", 12)

    session.stepReturn()

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "foo()V", 8)
  }

  // stepping out of anonymous functions
  @Test
  def StepIntoForComprehensionListStringInObjectMain(): Unit = {

    session = initDebugSession("ForComprehensionListString")

    session.runToLine(TYPENAME_FC_LS + "$", 9)

    session.checkStackFrame(TYPENAME_FC_LS + "$", "main([Ljava/lang/String;)V", 9)

    session.stepInto()

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$main$1", "apply(Ljava/lang/String;)I", 10)
  }

  // stepping out of anonymous functions
  @Test
  def StepReturnForComprehensionListStringInObjectMain(): Unit = {

    session = initDebugSession("ForComprehensionListString")

    session.runToLine(TYPENAME_FC_LS + "$", 10)

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$main$1", "apply(Ljava/lang/String;)I", 10)

    session.stepReturn()

    session.checkStackFrame(TYPENAME_FC_LS + "$", "main([Ljava/lang/String;)V", 13)
  }

  // Check that the jdi request created for a step over action are
  // correctly cleaned when the step is interrupted by a breakpoint.
  // Otherwise the whole system can hang.
  @Test(timeout = 10000)
  def StepOverWithBreakpoint_1001201(): Unit = {
    session = initDebugSession("AnonFunOnListInt")

    session.runToLine(TYPENAME_AF_LI + "$", 20)

    session.checkStackFrame(TYPENAME_AF_LI + "$$anonfun$main$5", "apply$mcVI$sp(I)V", 20)

    val breakpoint = session.addLineBreakpoint(TYPENAME_AF_LI + "$", 19)

    session.stepOver()

    session.checkStackFrame(TYPENAME_AF_LI + "$$anonfun$main$5", "apply(I)V", 19)

    session.stepOver()

    session.checkStackFrame(TYPENAME_AF_LI + "$$anonfun$main$5", "apply$mcVI$sp(I)V", 20)

    session.stepOver()

    session.checkStackFrame(TYPENAME_AF_LI + "$$anonfun$main$5", "apply(I)V", 19)

    session.removeBreakpoint(breakpoint)
  }

  @Test
  def StepIntoSkipsSetter(): Unit = {

    session = initDebugSession("StepFilters")

    session.runToLine(TYPENAME_STEP_FILTERS, 11)
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "mainTest()V", 11)

    session.stepInto()
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "mainTest()V", 13)
  }

  @Test
  def StepIntoSkipsGetter(): Unit = {

    session = initDebugSession("StepFilters")

    session.runToLine(TYPENAME_STEP_FILTERS, 13)
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "mainTest()V", 13)

    session.stepInto()
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "mainTest()V", 15)
  }

  @Test
  def StepIntoSkipsGetterAndSetterOnSameLine(): Unit = {

    session = initDebugSession("StepFilters")

    session.runToLine(TYPENAME_STEP_FILTERS, 15)
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "mainTest()V", 15)

    session.stepInto()
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "mainTest()V", 17)
  }

  @Test
  def StepIntoSkipsGetterAndSetterInArgList(): Unit = {

    session = initDebugSession("StepFilters")

    session.runToLine(TYPENAME_STEP_FILTERS, 17)
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "mainTest()V", 17)

    session.stepInto()
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "foo(Ljava/lang/String;Ljava/lang/String;)V", 8)
  }

  @Test
  def StepIntoSkipsGetterInsideFors(): Unit = {

    session = initDebugSession("StepFilters")

    session.runToLine(TYPENAME_STEP_FILTERS, 25)
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "fors()V", 25)

    session.stepInto()
    session.checkStackFrame(TYPENAME_STEP_FILTERS + "$$anonfun$fors$1", "apply(Ljava/lang/String;)V", 26)

    session.stepInto()
    session.checkStackFrame("debug.Helper$", "noop(Ljava/lang/Object;)V", 5)

    session.stepInto()
    session.checkStackFrame(TYPENAME_STEP_FILTERS + "$$anonfun$fors$1", "apply(Ljava/lang/String;)V", 27) // back inside for
  }

  @Test
  def StepIntoSkipsBridges(): Unit = {

    session = initDebugSession("StepFilters")

    session.runToLine(TYPENAME_STEP_FILTERS, 34)
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "bridges()V", 34)

    session.stepInto()
    session.checkStackFrame("stepping.Concrete", "base(I)I", 49)
  }

  @Test
  def StepIntoSkipsBridgesWithMultiExpressionsOnLine(): Unit = {

    session = initDebugSession("StepFilters")

    session.runToLine(TYPENAME_STEP_FILTERS, 37)
    session.checkStackFrame(TYPENAME_STEP_FILTERS, "bridges()V", 37)

    session.stepInto()
    session.checkStackFrame("stepping.Concrete", "base(I)I", 49)

    session.stepReturn()
  }

  @Test
  def StepIntoSkipsDefaultArgs(): Unit = {
    session = initDebugSession("MethodClassifiers")

    session.runToLine("stepping.MethodClassifiers", 60)
    session.stepInto()
    session.checkStackFrame("stepping.Defaults", "methWithDefaults(Ljava/lang/String;)V", 6)
  }

  @Test
  def StepIntoSkipsForwarder(): Unit = {
    session = initDebugSession("MethodClassifiers")
    session.runToLine("stepping.MethodClassifiers", 64)
    session.stepInto
    session.checkStackFrame("stepping.BaseTrait$class", "concreteTraitMethod1(Lstepping/BaseTrait;I)I", 12)
  }

  @Test
  def StepIntoSkipsForwarderWithParams(): Unit = {
    session = initDebugSession("MethodClassifiers")
    session.runToLine("stepping.MethodClassifiers", 67)
    session.stepInto
    session.checkStackFrame("stepping.BaseTrait$class", "concreteTraitMethod4(Lstepping/BaseTrait;IDLjava/lang/String;Ljava/lang/Object;)V", 15)
  }

  @Test
  def StepIntoSkipsForwarderWith22Params(): Unit = {
    session = initDebugSession("MethodClassifiers")
    session.runToLine("stepping.MethodClassifiers", 69)
    session.stepInto
    session.checkStackFrame("stepping.MaxArgs$class", "manyArgs(Lstepping/MaxArgs;DDDDDDDDDDDDDDDDDDDDDD)D", 105)
  }

  @Test
  def canDropToFrame(): Unit = {
    session = initDebugSession("SimpleStepping")

    session.runToLine(TYPENAME_SIMPLE_STEPPING, 12)

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "bar()V", 12)

    session.currentStackFrames.dropRight(1).foreach { frame =>
      assertTrue("Should be able to drop to frame", frame.canDropToFrame())
    }

    assertFalse("Shouldn't be able to drop to the last frame", session.currentStackFrames.last.canDropToFrame())

    // just check also the top stack frame when we stop in other place than the beginning of a method
    session.runToLine(TYPENAME_SIMPLE_STEPPING, 13)
    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "bar()V", 13)

    assertTrue("Should be able to drop to frame", session.currentStackFrames.head.canDropToFrame())
  }

  @Test
  def dropToFrame(): Unit = {

    def checkNumberOfFrames(count: Int): Unit =
      assertEquals("Wrong number of stack frames", count, session.currentStackFrames.size)

    session = initDebugSession("SimpleStepping")

    session.runToLine(TYPENAME_SIMPLE_STEPPING, 13)

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "bar()V", 13)
    checkNumberOfFrames(5)

    // return to the beginning of current method
    session dropToFrame session.currentStackFrame

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "bar()V", 12)
    checkNumberOfFrames(5)

    // drop to the same place
    session dropToFrame session.currentStackFrame

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "bar()V", 12)
    checkNumberOfFrames(5)

    // step back - 2 levels
    session dropToFrame session.currentStackFrames(2)

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "mainTest()V", 17)
    checkNumberOfFrames(3)

    // check that an application will be resumed from the correct place
    session.stepInto()
    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "foo()V", 8)
    session.runToLine(TYPENAME_SIMPLE_STEPPING, 13)
    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "bar()V", 13)

    // step back - 1 level
    session dropToFrame session.currentStackFrames(1)

    session.checkStackFrame(TYPENAME_SIMPLE_STEPPING, "foo()V", 8)
    checkNumberOfFrames(4)
  }
}
