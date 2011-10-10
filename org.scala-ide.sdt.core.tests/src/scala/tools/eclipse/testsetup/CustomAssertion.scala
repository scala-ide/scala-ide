package scala.tools.eclipse.testsetup

import scala.tools.eclipse.ScalaProject
import org.eclipse.jdt.core.ICompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.junit.Assert._

object CustomAssertion {

  /** Assert that no errors are reported for the passed `unit`.
   *  @param project Is needed to access the underlying compiler.
   *   
   *  @pre the `project` contains the passed `unit` 
   * */
  def assertNoErrors(project: ScalaProject, unit: ICompilationUnit) {
    project.doWithPresentationCompiler { compiler =>
      val oProblems = Option(unit.asInstanceOf[ScalaSourceFile].getProblems())
      for (problems <- oProblems if problems.nonEmpty) {
        val errMsg = problems.mkString("-", "\n", ".")
        fail("Found unexpected problem(s):\n" + errMsg)
      }
    }
  }
}
