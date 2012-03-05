package scala.tools.eclipse.debug

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.{Test, Before, After}
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor

object ScalaDebugSteppingTest extends TestProjectSetup("debug", bundleName= "org.scala-ide.sdt.debug.tests") {

  val TYPENAME_FC_LS = "stepping.ForComprehensionListString"
  val TYPENAME_FC_LS2 = "stepping.ForComprehensionListString2"
  val TYPENAME_FC_LO = "stepping.ForComprehensionListObject"
  val TYPENAME_FC_LI = "stepping.ForComprehensionListInt"
  val TYPENAME_AF_LS = "stepping.AnonFunOnListString"

}

class ScalaDebugSteppingTest {

  import ScalaDebugSteppingTest._

  var session: ScalaDebugTestSession = null

  @Before
  def setScalaDebugMode() {
    ScalaDebugPlugin.plugin.getPreferenceStore.setValue(DebugPreferencePage.P_ENABLE, true)
  }
  
  @Before
  def refreshBinaryFiles() {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }

  @After
  def cleanDebugSession() {
    if (session ne null) {
      session.terminate()
      session = null
    }
  }

  /*
   * Testing step over/in for comprehension through List[String]
   */

  @Test
  def StepOverIntoForComprehensionListStringInObjectMain() {

    session = new ScalaDebugTestSession(file("ForComprehensionListString.launch"))

    session.runToLine(TYPENAME_FC_LS + "$", 9)

    session.checkStackFrame(TYPENAME_FC_LS + "$", "main([Ljava/lang/String;)V", 9)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$main$1", "apply(Ljava/lang/String;)I", 10)
  }

  @Test
  def StepOverIntoForComprehensionListStringInObjectFoo() {

    session = new ScalaDebugTestSession(file("ForComprehensionListString.launch"))

    session.runToLine(TYPENAME_FC_LS + "$", 19)

    session.checkStackFrame(TYPENAME_FC_LS + "$", "foo(Lscala/collection/immutable/List;)V", 19)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$foo$1", "apply(Ljava/lang/String;)I", 20)
  }

  @Test
  def StepOverIntoForComprehensionListStringInClassConstructor() {

    session = new ScalaDebugTestSession(file("ForComprehensionListString.launch"))

    session.runToLine(TYPENAME_FC_LS, 29)

    session.checkStackFrame(TYPENAME_FC_LS, "<init>(Lscala/collection/immutable/List;)V", 29)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$1", "apply(Ljava/lang/String;)I", 30)
  }

  @Test
  def StepOverIntoForComprehensionListStringInClassBar() {

    session = new ScalaDebugTestSession(file("ForComprehensionListString.launch"))

    session.runToLine(TYPENAME_FC_LS, 35)

    session.checkStackFrame(TYPENAME_FC_LS, "bar()V", 35)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$bar$1", "apply(Ljava/lang/String;)I", 36)
  }

  /*
   * Testing step over/back in for comprehension through List[String]
   */

  @Test
  def StepOverBackInForComprehentionListString() {

    session = new ScalaDebugTestSession(file("ForComprehensionListString.launch"))

    session.runToLine(TYPENAME_FC_LS + "$", 10)

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$main$1", "apply(Ljava/lang/String;)I", 10)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS + "$$anonfun$main$1", "apply(Ljava/lang/String;)I", 10)
  }

  /*
   * Testing step over/out for comprehension through List[String]
   */

  @Test
  def StepOverOutForComprehentionListString() {

    session = new ScalaDebugTestSession(file("ForComprehensionListString2.launch"))

    session.runToLine(TYPENAME_FC_LS2 + "$", 12)

    session.checkStackFrame(TYPENAME_FC_LS2 + "$$anonfun$main$1", "apply(Ljava/lang/String;)I", 12)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LS2 + "$", "main([Ljava/lang/String;)V", 15)
  }

  /*
   * Testing step over/in for comprehension through List[Object]
   */

  @Test
  def StepOverIntoForComprehensionListObjectInObjectMain() {

    session = new ScalaDebugTestSession(file("ForComprehensionListObject.launch"))

    session.runToLine(TYPENAME_FC_LO + "$", 9)

    session.checkStackFrame(TYPENAME_FC_LO + "$", "main([Ljava/lang/String;)V", 9)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LO + "$$anonfun$main$1", "apply(Ljava/lang/Object;)Ljava/lang/Object;", 10)
  }

  @Test
  def StepOverIntoForComprehensionListObjectInObjectFoo() {

    session = new ScalaDebugTestSession(file("ForComprehensionListObject.launch"))

    session.runToLine(TYPENAME_FC_LO + "$", 19)

    session.checkStackFrame(TYPENAME_FC_LO + "$", "foo(Lscala/collection/immutable/List;)V", 19)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LO + "$$anonfun$foo$1", "apply(Ljava/lang/Object;)Ljava/lang/Object;", 20)
  }

  @Test
  def StepOverIntoForComprehensionListObjectInClassConstructor() {

    session = new ScalaDebugTestSession(file("ForComprehensionListObject.launch"))

    session.runToLine(TYPENAME_FC_LO, 29)

    session.checkStackFrame(TYPENAME_FC_LO, "<init>(Lscala/collection/immutable/List;)V", 29)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LO + "$$anonfun$1", "apply(Ljava/lang/Object;)Ljava/lang/Object;", 30)
  }

  @Test
  def StepOverIntoForComprehensionListObjectInClassBar() {

    session = new ScalaDebugTestSession(file("ForComprehensionListObject.launch"))

    session.runToLine(TYPENAME_FC_LO, 35)

    session.checkStackFrame(TYPENAME_FC_LO, "bar()V", 35)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LO + "$$anonfun$bar$1", "apply(Ljava/lang/Object;)Ljava/lang/Object;", 36)
  }

  /*
   * Testing step over/in for comprehension through List[Int]
   */

  @Test
  def StepOverIntoForComprehensionListIntInObjectMain() {

    session = new ScalaDebugTestSession(file("ForComprehensionListInt.launch"))

    session.runToLine(TYPENAME_FC_LI + "$", 11)

    session.checkStackFrame(TYPENAME_FC_LI + "$", "main([Ljava/lang/String;)V", 11)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LI + "$$anonfun$main$1", "apply$mcVI$sp(I)V", 12)
  }

  @Test
  def StepOverIntoForComprehensionListIntInObjectFoo() {

    session = new ScalaDebugTestSession(file("ForComprehensionListInt.launch"))

    session.runToLine(TYPENAME_FC_LI + "$", 21)

    session.checkStackFrame(TYPENAME_FC_LI + "$", "foo(Lscala/collection/immutable/List;)V", 21)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LI + "$$anonfun$foo$1", "apply$mcVI$sp(I)V", 22)
  }

  @Test
  def StepOverIntoForComprehensionListIntInClassConstructor() {

    session = new ScalaDebugTestSession(file("ForComprehensionListInt.launch"))

    session.runToLine(TYPENAME_FC_LI, 31)

    session.checkStackFrame(TYPENAME_FC_LI, "<init>(Lscala/collection/immutable/List;)V", 31)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LI + "$$anonfun$1", "apply$mcVI$sp(I)V", 32)
  }

  @Test
  def StepOverIntoForComprehensionListIntInClassBar() {

    session = new ScalaDebugTestSession(file("ForComprehensionListInt.launch"))

    session.runToLine(TYPENAME_FC_LI, 37)

    session.checkStackFrame(TYPENAME_FC_LI, "bar()V", 37)

    session.stepOver()

    session.checkStackFrame(TYPENAME_FC_LI + "$$anonfun$bar$1", "apply$mcVI$sp(I)V", 38)
  }

  /*
   * Testing step over/in List[String] methods
   */

  @Test
  def StepOverIntoListStringForEach() {

    session = new ScalaDebugTestSession(file("AnonFunOnListString.launch"))

    session.runToLine(TYPENAME_AF_LS + "$", 11)

    session.checkStackFrame(TYPENAME_AF_LS + "$", "main([Ljava/lang/String;)V", 11)

    session.stepOver()

    session.checkStackFrame(TYPENAME_AF_LS + "$$anonfun$main$1", "apply(Ljava/lang/String;)V", 11)
  }

  @Test
  def StepOverIntoListStringFind() {

    session = new ScalaDebugTestSession(file("AnonFunOnListString.launch"))

    session.runToLine(TYPENAME_AF_LS + "$", 13)

    session.checkStackFrame(TYPENAME_AF_LS + "$", "main([Ljava/lang/String;)V", 13)

    session.stepOver()

    session.checkStackFrame(TYPENAME_AF_LS + "$$anonfun$main$2", "apply(Ljava/lang/String;)Z", 13)
  }

  @Test
  def StepOverIntoListStringMap() {

    session = new ScalaDebugTestSession(file("AnonFunOnListString.launch"))

    session.runToLine(TYPENAME_AF_LS + "$", 15)

    session.checkStackFrame(TYPENAME_AF_LS + "$", "main([Ljava/lang/String;)V", 15)

    session.stepOver()

    session.checkStackFrame(TYPENAME_AF_LS + "$$anonfun$main$3", "apply(Ljava/lang/String;)I", 15)
  }

  @Test
  def StepOverIntoListStringFoldLeft() {

    session = new ScalaDebugTestSession(file("AnonFunOnListString.launch"))

    session.runToLine(TYPENAME_AF_LS + "$", 17)

    session.checkStackFrame(TYPENAME_AF_LS + "$", "main([Ljava/lang/String;)V", 17)

    session.stepOver()

    session.checkStackFrame(TYPENAME_AF_LS + "$$anonfun$main$4", "apply(ILjava/lang/String;)I", 17)
  }

}