package org.scalaide.refactoring.internal

import org.eclipse.jface.action.IAction
import org.eclipse.ltk.ui.refactoring.RefactoringWizard
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.PlatformUI
import scala.tools.refactoring.common.TextChange
import org.scalaide.core.internal.jdt.model.ScalaSourceFile

/**
 * This is the abstract driver of a refactoring execution: it is the
 * entry point when a refactoring is executed and manages the wizards.
 *
 * Each concrete refactoring action needs to implement the abstract
 * `createRefactoring` method which instantiates a ScalaIdeRefactoring.
 *
 * There are two important subclasses called [[RefactoringActionWithWizard]] and
 * [[RefactoringActionWithoutWizard]] that can be used instead of this class.
 */
trait RefactoringAction extends ActionAdapter {

  /**
   * This factory method needs to be implemented by subclasses to
   * construct an appropriate ScalaIdeRefactoring instance.
   */
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile): ScalaIdeRefactoring

  /**
   * Creates a ScalaRefactoringWizard for this ScalaIdeRefactoring instance.
   *
   * @param refactoring An optional ScalaIdeRefactoring instance.
   * @return When None is passed, an instance of ErrorRefactoringWizard is returned.
   */
  def createWizardForRefactoring(refactoring: Option[ScalaIdeRefactoring]): RefactoringWizard = {
    refactoring map (new ScalaRefactoringWizard(_)) getOrElse (new ErrorRefactoringWizard("Error, not a text editor."))
  }

  /**
   * Creates a ScalaIdeRefactoring for the current active editor and selection. If no appropriate
   * editor can be found of if it's not possible to create a ScalaSourceFile for this editor,
   * None is returned.
   */
  def createScalaIdeRefactoringForCurrentEditorAndSelection(): Option[ScalaIdeRefactoring] = {
    import EditorHelpers._

    withScalaSourceFileAndSelection { (scalaFile, selection) =>
      Some(createRefactoring(selection.getOffset, selection.getOffset + selection.getLength, scalaFile))
    }
  }

  /**
   * Runs the given wizard in a RefactoringWizardOpenOperation and the current shell.
   *
   * Some of the refactoring implementations don't run in a wizard but make use of the
   * linked mode ui. These refactorings call `enterLinkedModeUi` directly.
   */
  def runRefactoring(wizard: RefactoringWizard, shell: Shell) {
    new RefactoringWizardOpenOperation(wizard).run(shell, "Scala Refactoring")
  }
}
