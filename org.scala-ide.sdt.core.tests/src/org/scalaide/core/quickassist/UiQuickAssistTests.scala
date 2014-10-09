package org.scalaide.core
package quickassist

import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext
import org.junit.Assert._
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.internal.quickassist.QuickAssistProcessor

import testsetup.TestProjectSetup

/**
 * Provides test behavior that relies on a working UI environment.
 */
object UiQuickAssistTests extends TestProjectSetup("quickassist") {

  def assertNumberOfProblems(nProblems: Int, problems: Array[IProblem]) {
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
    val unit = compilationUnit(pathToSource).asInstanceOf[ScalaCompilationUnit]

    unit.withSourceFile { (src, compiler) =>
      compiler.askReload(List(unit)).get

      val problems = compiler.problemsOf(unit)
      assertTrue("No problems found.", problems.nonEmpty)
      assertNumberOfProblems(expectedQuickFixesList.size, problems.toArray)

      val part = JavaUI.openInEditor(unit.getCompilationUnit)

      for ((problem, expectedQuickFixes) <- problems zip expectedQuickFixesList) {
        val offset = problem.getSourceStart
        val length = problem.getSourceEnd + 1 - offset
        val processor = new QuickAssistProcessor(part.getEditorInput, QuickAssistProcessor.DefaultId)
        val proposals = processor.computeQuickAssistProposals(new IQuickAssistInvocationContext {
          override def getOffset = offset
          override def getLength = length
          override def getSourceViewer = null
        })
        val corrections = proposals.map(_.getDisplayString)

        for (quickFix <- expectedQuickFixes) {
          assertTrue("Quick fix " + quickFix + " was not offered. Offered were: " + corrections.mkString(", "),
            corrections contains quickFix)
        }
      }
    }
  }
}
