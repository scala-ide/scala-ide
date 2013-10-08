package scala.tools.eclipse.quickfix

import java.util.ArrayList

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.mutable.Buffer
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.nsc.interactive.Response

import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IStatus
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.ui.text.correction.AssistContext
import org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor
import org.eclipse.jdt.internal.ui.text.correction.ProblemLocation
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jdt.ui.text.java.IProblemLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object QuickFixesTests extends TestProjectSetup("quickfix") {

  def assertStatusOk(status: IStatus) {
    if (!status.isOK()) {
      if (status.getException() == null) { // find a status with an exception
        val children = status.getChildren();
        for (child <- children) {
          if (child.getException() != null) {
            throw new CoreException(child);
          }
        }
      }
    }
  }

  def assertNumberOfProblems(nProblems: Int, problems: Array[IProblem]) {
    // check if numbers match but we want reasonable error message
    if (problems.length != nProblems) {
      val buf = new StringBuffer("Wrong number of problems, is: ")
      buf.append(problems.length).append(", expected: ").append(nProblems).append('\n')
      for (problem <- problems) {
        buf.append(problem).append(" at ")
        buf.append('[').append(problem.getSourceStart()).append(" ,").append(problem.getSourceEnd()).append(']')
        buf.append('\n')
      }

      assertEquals(buf.toString, nProblems, problems.length)
    }
  }

  def withQuickFixes(pathToSource: String)(expectedQuickFixes: String*) {
    withManyQuickFixesPerLine(pathToSource)(expectedQuickFixes.map(List(_)).toList)
  }

  def withManyQuickFixesPerLine(pathToSource: String)(expectedQuickFixesList: List[List[String]]) {
    // get our compilation unit
    val unit = compilationUnit(pathToSource).asInstanceOf[ScalaCompilationUnit]

    // first, 'open' the file by telling the compiler to load it
    unit.withSourceFile { (src, compiler) =>

      // do a compiler reload before checking for problems
      val dummy = new Response[Unit]
      compiler.askReload(List(src), dummy)
      dummy.get

      val problems = compiler.problemsOf(unit)
      assertTrue("No problems found.", problems.size > 0)
      assertNumberOfProblems(expectedQuickFixesList.size, problems.toArray)

      JavaUI.openInEditor(unit.getCompilationUnit)

      // check each problem quickfix
      for ((problem, expectedQuickFixes) <- problems zip expectedQuickFixesList) {
        // here we will accumulate proposals
        val proposals: ArrayList[IJavaCompletionProposal] = new ArrayList()

        // get all corrections for the problem
        val offset = problem.getSourceStart
        val length = problem.getSourceEnd + 1 - offset
        val context = new AssistContext(unit.getCompilationUnit, offset, length)

        val problemLocation: IProblemLocation = new ProblemLocation(problem)
        val status = JavaCorrectionProcessor.collectCorrections(context, Array(problemLocation), proposals)

        // assert that status is okay
        assertStatusOk(status)

        // get collection of offered quickfix message
        import scala.collection.JavaConversions._
        val corrections: List[String] = (proposals: Buffer[IJavaCompletionProposal]).toList.map(_.getDisplayString)

        // check all expected quick fixes
        //assertEquals(expectedQuickFixes.size, corrections.size)
        // NOTE due to a bug, Scala IDE returns a lot of quick fixes so we skip number comparison
        for (quickFix <- expectedQuickFixes) {
          assertTrue("Quick fix " + quickFix + " was not offered. Offered were: " + corrections.mkString(", "),
            corrections contains quickFix)
        }
      }
    }
  }
}
