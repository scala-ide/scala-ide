/*
 * Copyright 2011 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring

import org.eclipse.core.runtime.Status
import org.eclipse.jface.action.IAction

/**
 * This trait can be mixed in to a RefactoringAction if the refactoring
 * should be performed without showing the wizard and the change preview.
 *
 * If an error occurs during preparation, the wizard is shown with the
 * error message.
 */
trait ActionWithNoWizard {
  this: RefactoringAction =>

  override def run(action: IAction) {
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