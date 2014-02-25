package org.scalaide.core.testsetup

import org.scalaide.core.internal.project.ScalaProject
import org.eclipse.jdt.core.ICompilationUnit
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
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
