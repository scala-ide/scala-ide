/*
 * Copyright 2011 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring

import org.eclipse.core.runtime.{Status, IStatus, IProgressMonitor}
import org.eclipse.jface.action.IAction
import org.eclipse.ui.progress.UIJob
import org.eclipse.ui.PlatformUI
import org.eclipse.swt.widgets.Shell
import org.eclipse.jface.operation.IRunnableWithProgress
import org.eclipse.jface.dialogs.ProgressMonitorDialog

/**
 * This trait can be mixed in to a RefactoringAction if the refactoring
 * should be performed without showing the wizard and the change preview.
 * 
 * If an error occurs during preparation, the wizard is shown with the
 * error message. 
 */
trait ActionWithNoWizard {
  this: RefactoringAction =>

  val shell = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell

  override def run(action: IAction) {
    runRefactoringInUiJob()
  }
  
  def runRefactoringInUiJob() {
    runInUiJob { (pm, shell) =>
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
  
  def runInUiJob(block: (IProgressMonitor, Shell) => IStatus) {
    new UIJob("Refactoring") {
      def runInUIThread(pm: IProgressMonitor): IStatus = {
        block(pm, shell)
      }
    }.schedule
  }
  
  def runInProgressDialog(block: IProgressMonitor => Unit) {
    
    val runnable = new IRunnableWithProgress {
      def run(pm: IProgressMonitor) = block(pm)
    }
    
    val dialog = new ProgressMonitorDialog(PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell)
    dialog.run(false /*run in ui thread!*/, true /*cancelable*/, runnable)
  }
}