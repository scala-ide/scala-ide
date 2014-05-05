package org.scalaide.refactoring.internal

import org.eclipse.ui.PlatformUI

/**
 * This trait can be used if the refactoring should be performed by showing the
 * wizard and the change preview.
 *
 * If an error occurs during preparation then the wizard is updated with the
 * error message.
 *
 * In contrast, [[RefactoringExecutorWithoutWizard]] can be used if no wizard
 * should be shown during performing the refactoring.
 */
trait RefactoringExecutorWithWizard extends RefactoringExecutor {

  /**
   * Performs the refactoring and shows a wizard with the change preview.
   */
  override def perform(): Unit = {
    val shell = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell

    runRefactoring(createWizardForRefactoring(
        createScalaIdeRefactoringForCurrentEditorAndSelection()), shell)
  }
}
