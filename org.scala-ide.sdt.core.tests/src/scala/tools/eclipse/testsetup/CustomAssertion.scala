package scala.tools.eclipse.testsetup

import scala.tools.eclipse.ScalaProject
import org.eclipse.jdt.core.ICompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.junit.Assert._

trait CustomAssertion extends TestProjectSetup {

  /** Assert that no errors are reported for the passed `unit`. */
  def assertNoErrors(unit: ScalaSourceFile) {
    val oProblems = Option(unit.getProblems())
    for (problems <- oProblems if problems.nonEmpty) {
      val errMsg = problems.mkString("-", "\n", ".")
      fail("Found unexpected problem(s):\n" + errMsg)
    }
  }
}
