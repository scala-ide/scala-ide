/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import org.eclipse.ltk.core.refactoring.Refactoring
import org.eclipse.ltk.ui.refactoring.RefactoringWizard

class ScalaRefactoringWizard(refactoring: ScalaIdeRefactoring, flags: Int = RefactoringWizard.DIALOG_BASED_USER_INTERFACE) extends RefactoringWizard(refactoring, flags) {
  def addUserInputPages() {
    refactoring.getPages foreach addPage
  }
}
  