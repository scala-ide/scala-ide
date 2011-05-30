/*
 * Copyright 2011 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring

import org.eclipse.jface.action.IAction
import org.eclipse.core.runtime.NullProgressMonitor

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
    createScalaIdeRefactoringForCurrentEditorAndSelection() match {
      case Some(refactoring: ScalaIdeRefactoring) =>
        val npm = new NullProgressMonitor
        val status = refactoring.checkInitialConditions(npm)
        
        if(status.hasError) {
          runRefactoring(createWizardForRefactoring(Some(refactoring)))
        } else {
          refactoring.createChange(npm).perform(npm)
        }
      case _ => 
        runRefactoring(new ErrorRefactoringWizard("An error occurred while creating the refactoring."))
    }
  }
}