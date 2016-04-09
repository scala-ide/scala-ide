package org.scalaide.refactoring.internal

import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations
import org.scalaide.core.internal.statistics.Features.NotSpecified

/**
 * The Inline Local -- also known as Inline Temp -- refactoring is the dual to Extract Local.
 * It can be used to eliminate a local values by replacing all references to the local value
 * by its right hand side.
 *
 * The implementation does not show a wizard but directly applies the changes (ActionWithNoWizard trait).
 */
class InlineLocal extends RefactoringExecutor with RefactoringExecutorWithoutWizard {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) =
    new InlineLocalScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class InlineLocalScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
      // feature marked as [[NotSpecified]] because it is already categorized as quick assist
      extends ScalaIdeRefactoring(NotSpecified, "Inline Local", file, start, end) {

    val refactoring = file.withSourceFile((sourceFile, compiler) => new implementations.InlineLocal with GlobalIndexes {
      val global = compiler
      val index = {
        val tree = askLoadedAndTypedTreeForFile(sourceFile).left.get
        global.ask(() => GlobalIndex(tree))
      }
    }) getOrElse fail()

    /**
     * The refactoring does not take any parameters.
     */
    def refactoringParameters = new refactoring.RefactoringParameters
  }
}
