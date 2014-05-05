package org.scalaide.refactoring.internal

import org.eclipse.core.runtime.Status

/**
 * This trait can be used if the refactoring should be performed without showing
 * the wizard and the change preview.
 *
 * If an error occurs during preparation, a wizard is shown with the
 * error message.
 *
 * In contrast, [[RefactoringExecutorWithWizard]] can be used if a wizard should
 * be shown.
 */
trait RefactoringExecutorWithoutWizard extends RefactoringExecutor {

  /**
   * Performs the refactoring without showing a wizard. All changes are applied
   * immediately.
   */
  override def perform(): Unit = {
    runRefactoringInUiJob()
  }

  def runRefactoringInUiJob() {
    ProgressHelpers.runInUiJob { (pm, shell) =>
      createScalaIdeRefactoringForCurrentEditorAndSelection() match {
        case Some(refactoring: ScalaIdeRefactoring) =>
          val status = refactoring.checkInitialConditions(pm)

          if(status.hasError || status.hasWarning) {
            runRefactoring(createWizardForRefactoring(Some(refactoring)), shell)
          } else {
            refactoring.createChange(pm).perform(pm)
          }
        case None =>
          runRefactoring(new ErrorRefactoringWizard("An error occurred while creating the refactoring."), shell)
      }

      Status.OK_STATUS
    }
  }
}
