/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.refactoring.implementations.ExpandCaseClassBinding

/**
 * A refactoring that expands bindings to case-classes in pattern matches with
 * the corresponding extractor. The refactoring is only accessible through a
 * quickfix.
 *
 * Does not show a wizard but directly applies the changes (ActionWithNoWizard trait).
 */
class ExpandCaseClassBindingAction extends RefactoringAction with RefactoringActionWithoutWizard {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) =
    new ExpandCaseClassBindingScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class ExpandCaseClassBindingScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
    extends ScalaIdeRefactoring("Expand Case Class Binding", file, start, end) {

    val refactoring = file.withSourceFile((_, compiler) => new ExpandCaseClassBinding {
      val global = compiler
    }) getOrElse fail()

    /**
     * The refactoring does not take any parameters.
     */
    def refactoringParameters = new refactoring.RefactoringParameters
  }
}
