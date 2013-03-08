/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import org.eclipse.ltk.core.refactoring.Refactoring
import org.eclipse.ltk.ui.refactoring.RefactoringWizard

/**
 *  Wraps the `ScalaIdeRefactoring` instance in a wizard and adds
 *  the pages from the refactoring to the wizard.
 */
class ScalaRefactoringWizard(refactoring: ScalaIdeRefactoring, flags: Int = RefactoringWizard.DIALOG_BASED_USER_INTERFACE) extends RefactoringWizard(refactoring, flags) {
  def addUserInputPages() {
    refactoring.getPages foreach addPage
  }
}
