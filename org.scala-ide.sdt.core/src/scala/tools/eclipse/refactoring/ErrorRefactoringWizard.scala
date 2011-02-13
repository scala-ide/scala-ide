/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import org.eclipse.ltk.ui.refactoring.RefactoringWizard
import org.eclipse.ltk.core.refactoring.Refactoring
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.RefactoringStatus

private class FailingRefactoring(msg: String) extends Refactoring {
      
  def fail = throw new Exception(msg)
    
  def createChange(pm: IProgressMonitor) = fail
    
  def checkInitialConditions(pm: IProgressMonitor) = RefactoringStatus.createFatalErrorStatus(msg)
    
  def checkFinalConditions(pm: IProgressMonitor) = fail
    
  val getName = msg
}

class ErrorRefactoringWizard(msg: String = "Error, not a text editor.") extends 
    RefactoringWizard(new FailingRefactoring(msg), RefactoringWizard.WIZARD_BASED_USER_INTERFACE) { 
  def addUserInputPages() = ()
}
