package org.scalaide.refactoring.internal

import scala.tools.refactoring.implementations
import org.scalaide.core.internal.jdt.model.ScalaSourceFile

/**
 * A refactoring that expands bindings to case-classes in pattern matches with
 * the corresponding extractor. The refactoring is only accessible through a
 * quickfix.
 */
class ExpandCaseClassBinding extends RefactoringExecutor with RefactoringExecutorWithoutWizard {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) =
    new ExpandCaseClassBindingScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class ExpandCaseClassBindingScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
    extends ScalaIdeRefactoring("Expand Case Class Binding", file, start, end) {

    val refactoring = file.withSourceFile((_, compiler) => new implementations.ExpandCaseClassBinding {
      val global = compiler
    }) getOrElse fail()

    /**
     * The refactoring does not take any parameters.
     */
    def refactoringParameters = new refactoring.RefactoringParameters
  }
}
