package org.scalaide.core
package pc

import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import testsetup.SDTTestUtils
import org.scalaide.logging.Logger
import scala.tools.nsc.interactive.InteractiveReporter
import org.eclipse.jdt.core.ICompilationUnit
import org.junit._
import org.mockito.Matchers._
import org.mockito.Mockito._
import hyperlink.HyperlinkTester
import testsetup.CustomAssertion
import org.scalaide.core.compiler.InteractiveCompilationUnit

object PresentationCompilerTest extends testsetup.TestProjectSetup("pc") with CustomAssertion with HyperlinkTester

class PresentationCompilerTest {
  import PresentationCompilerTest._

  @Before
  def reset() {
    project.presentationCompiler.shutdown()
  }

  @Test
  def creatingOverrideIndicator_ShouldNotReportError_t1000531() {
    // when
    val unit = open("t1000531/A.scala")
    val mockLogger = mock(classOf[Logger])

    // then
    unit.withSourceFile { (sourceFile, compiler) =>
      compiler.withStructure(sourceFile, keepLoaded = true) { tree =>
        compiler.askOption { () =>
          val overrideIndicatorBuilder = new compiler.OverrideIndicatorBuilderTraverser(unit, new java.util.HashMap) {
            override val eclipseLog = mockLogger
          }
          // if the unit is not kept loaded (i.e., `keepLoaded = false`), then a message
          // "Error creating override indicators" is reported. That is why this test checks
          // that no error is reported to the mocked logger.
          overrideIndicatorBuilder.traverse(tree)
        }
      }
    }

    // verify
    verify(mockLogger, times(0)).error(any())
  }

  private def managedUnits(): Set[InteractiveCompilationUnit] = project.presentationCompiler(_.compilationUnits.toSet).orNull

  @Test
  // garillot: deactivated pending replication of a platform test architecture
  @Ignore("Enable this test once headless triggering of Reconciler is possible")
  def freshFileReportsErrors() {
    val contentsWithErrors = """
package t1001094

class FreshFile {
  val x: ListBuffer[Int] = null
}"""
    val unit = open("t1001094/FreshFile.scala")
    val errors = unit.currentProblems()
    Assert.assertEquals("Unexpected errors", errors, Nil)

    unit.getBuffer().setContents(contentsWithErrors)
    SDTTestUtils.waitUntil(5000)(unit.currentProblems ne Nil)
    val errors1 = unit.currentProblems()
    Assert.assertNotSame("Unexpected clean source", errors1, Nil)
  }

  @Test
  def implicitConversionFromPackageObjectShouldBeInScope_t1000647() {
    //when
    open("t1000647/foo/package.scala")

    // then
    val dataFlowUnit = open("t1000647/bar/DataFlow.scala")

    // give a chance to the background compiler to report the error
    waitUntilTypechecked(dataFlowUnit)

    // verify
    assertNoErrors(dataFlowUnit)
  }

  @Test
  def illegalCyclicReferenceInvolvingObject_t1000658() {
    //when
    val unit = scalaCompilationUnit("t1000658/ThreadPoolConfig.scala")
    //then
    reload(unit)
    // verify
    assertNoErrors(unit)
  }

  @Test
  def notEnoughArgumentsForCconstructorError_ShouldNotBeReported_t1000692() {
    //when
    val unit = scalaCompilationUnit("t1000692/akka/util/ReflectiveAccess.scala")
    val oracle = List(Link("class t1000692.akka.config.ModuleNotAvailableException"))
    //then
    // it is important to ask hyperlinking before reloading!
    loadTestUnit(unit, forceTypeChecking = false).andCheckAgainst(oracle)
    reload(unit)
    // verify
    assertNoErrors(unit)
  }

  @Test
  def pcShouldReportTheCorrectCompilationUnitsItKnowsAbout() {
    // should be empty
    Assert.assertTrue("Presentation compiler should not maintain any units after a shutdown request", managedUnits().isEmpty)

    val cu = scalaCompilationUnit("t1000692/akka/util/ReflectiveAccess.scala")

    // still no units should be loaded
    Assert.assertTrue("Presentation compiler should not maintain any units after structure build (%s)".format(managedUnits()), managedUnits().isEmpty)

    cu.scheduleReconcile().get

    // now the unit should be managed
    Assert.assertEquals("Presentation compiler should maintain one unit after reload (%s)".format(managedUnits()), 1, managedUnits().size)
  }

  @Test
  def pcShouldReportTheCorrectCompilationUnitsOnShutdown() {
    val cu = scalaCompilationUnit("t1000692/akka/util/ReflectiveAccess.scala")
    val cu1 = scalaCompilationUnit("t1000658/ThreadPoolConfig.scala")

    Seq(cu, cu1).foreach(_.scheduleReconcile().get)

    Assert.assertEquals("Managed compilation units", Set(cu, cu1), managedUnits())

    project.presentationCompiler.askRestart()
    val returned = managedUnits()

    // now the unit should be managed
    Assert.assertEquals("Presentation compiler should report one unit on shutdown", Set(cu, cu1), returned)
  }

  @Test
  def pcShouldReloadAllUnitsOnReset() {
    val cu = scalaCompilationUnit("t1000692/akka/util/ReflectiveAccess.scala")
    val cu1 = scalaCompilationUnit("t1000658/ThreadPoolConfig.scala")

    Seq(cu, cu1).foreach(_.scheduleReconcile().get)

    Assert.assertEquals("Managed compilation units", Set(cu, cu1), managedUnits())

    project.presentationCompiler.askRestart()

    // now the unit should be managed
    Assert.assertEquals("Presentation compiler should report one unit on shutdown", Set(cu, cu1), managedUnits())
  }

  @Test
  def correctlyTypecheckClassesWithDefaultArguments_t1000976() {
    def openUnitAndTypecheck(path: String): ScalaSourceFile = {
      val unit = scalaCompilationUnit(path).asInstanceOf[ScalaSourceFile]
      unit.reload()
      waitUntilTypechecked(unit)
      unit
    }

    // SUT: Opening A.scala w/ full typecheck and then B.scala determines the "ghost error" to show up
    openUnitAndTypecheck("t1000976/a/A.scala")
    val unitB = openUnitAndTypecheck("t1000976/b/B.scala")

    // verify
    assertNoErrors(unitB)
  }
}
