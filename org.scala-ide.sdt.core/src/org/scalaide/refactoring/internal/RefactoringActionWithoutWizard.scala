package org.scalaide.refactoring.internal

import org.eclipse.core.runtime.Status
import org.eclipse.jface.action.IAction

/**
 * This trait is a RefactoringAction that can be used if the refactoring
 * should be performed without showing the wizard and the change preview.
 *
 * If an error occurs during preparation, a wizard is shown with the
 * error message.
 *
 * In contrast, [[RefactoringActionWithWizard]] can be used if a wizard should
 * be shown.
 */
trait RefactoringActionWithoutWizard extends RefactoringAction {

  /**
   * Performs the refactoring without showing a wizard. All changes are applied
   * immediately.
   */
  def run(action: IAction) {
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
        case _ =>
          runRefactoring(new ErrorRefactoringWizard("An error occurred while creating the refactoring."), shell)
      }

      Status.OK_STATUS
    }
  }
}
