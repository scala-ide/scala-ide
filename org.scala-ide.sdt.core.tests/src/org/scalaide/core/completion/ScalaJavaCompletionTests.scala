package org.scalaide.core.completion

import org.scalaide.core.testsetup.TestProjectSetup
import org.junit.Ignore
import org.junit.Test
import org.junit.Assert._
import org.scalaide.core.testsetup.SDTTestUtils
import org.eclipse.core.runtime.NullProgressMonitor
import org.scalaide.core.internal.completion.ScalaJavaCompletionProposalComputer

object ScalaJavaCompletionTests extends TestProjectSetup("completion")

// FIXME: Test in this class fails, but only comment why was "Uncomment as soon as this regression is fixed."
// I prefer ignored tests to commented out. Jerzy Müller, 27.05.2015
class ScalaJavaCompletionTests {
  import ScalaJavaCompletionTests._

  private def runTest(pathToClass: String, expectedCompletions: List[List[String]]): Unit = {
    // get the unit
    val unit = compilationUnit(pathToClass)
    // make it a working copy
    unit.becomeWorkingCopy(new NullProgressMonitor)

    val completionProposalComputer = new ScalaJavaCompletionProposalComputer

    // get the marker positions
    val positions = SDTTestUtils.positionsOf(unit.getBuffer.getContents.toCharArray(), "/*!*/")

    // check the test setup
    assertEquals("Different number of expected completions and completion locations", expectedCompletions.length, positions.length)

    for (i <- 0 until positions.size) {

      // get the proposal
      val proposals = completionProposalComputer.mixedInCompletions(unit, positions(i) + 1, new NullProgressMonitor)

      import scala.collection.JavaConverters._

      // extract the data and sort
      val resultCompletion = proposals.asScala.map(p => p.getDisplayString).sorted

      // check the completions
      assertEquals("Wrong set of completions for " + i, expectedCompletions(i), resultCompletion)
    }

  }

  val noCompletion = List[String]()
  val oneCompletion = List("getX(): String")
  val allCompletions = List("getX(): String", "setX(String): Unit", "x: String", "x_=(String): Unit")

  /**
   * Test the completion when trying to call the method on a reference.
   */
  @Ignore
  @Test
  def ticket1000412_reference(): Unit = {
    val oracle = List(
      noCompletion, // outsideTypeDeclaration
      //allCompletions, // var1
      oneCompletion, // var2
      oneCompletion, // var3
      oneCompletion, // foo1
      allCompletions, // foo2
      oneCompletion, // foo3
      oneCompletion, // foo4
      oneCompletion, // foo5
      noCompletion, // foo6
      oneCompletion, // foo7
      allCompletions, // foo8
      allCompletions, // foo9
      allCompletions, // foo10
      allCompletions, // foo11
      allCompletions, // foo12
      oneCompletion // foo13
      )

    reload(scalaCompilationUnit("ticket_1000412/model/ClassA.scala"))

    runTest("ticket_1000412/test/TestJavaReference.java", oracle)
  }

  /**
   * Test the completion when trying to call the method when the class extends the class containing the method.
   */
  @Ignore
  @Test
  def ticket1000412_extends(): Unit = {
    val oracle = List(
      allCompletions, // var11
      oneCompletion, // var12
      oneCompletion, // var13
      allCompletions, // bar1
      oneCompletion, // bar2
      allCompletions, // bar3
      oneCompletion, // bar4
      oneCompletion, // bar5
      oneCompletion, // bar6
      allCompletions, // bar7
      oneCompletion, // bar8
      allCompletions, // bar9
      oneCompletion, // bar10
      oneCompletion // bar11
      )

    reload(scalaCompilationUnit("ticket_1000412/model/ClassA.scala"))

    runTest("ticket_1000412/test/TestJavaExtends.java", oracle)
  }
}
