package org.scalaide.refactoring.internal

import org.eclipse.jface.action.IAction
import org.eclipse.ui.PlatformUI

/**
 * This trait is a RefactoringAction that can be used if the refactoring
 * should be performed by showing the wizard and the change preview.
 *
 * If an error occurs during preparation then the wizard is updated with the
 * error message.
 *
 * In contrast, [[RefactoringActionWithoutWizard]] can be used if no wizard
 * should be shown during performing the refactoring.
 */
trait RefactoringActionWithWizard extends RefactoringAction {

  /**
   * Performs the refactoring and shows a wizard with the change preview.
   */
  def run(action: IAction) {
    val shell = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell

    runRefactoring(createWizardForRefactoring(
        createScalaIdeRefactoringForCurrentEditorAndSelection()), shell)
  }
}
