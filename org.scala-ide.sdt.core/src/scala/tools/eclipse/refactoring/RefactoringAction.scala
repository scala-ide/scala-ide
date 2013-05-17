/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring

import org.eclipse.ltk.ui.refactoring.RefactoringWizard
import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.Refactoring
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.action.IAction
import org.eclipse.ui.IEditorActionDelegate
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.ui.PlatformUI
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.jface.text.link._
import org.eclipse.swt.widgets.Shell

/**
 * This is the abstract driver of a refactoring execution: it is the
 * entry point when a refactoring is executed and manages the wizards.
 *
 * Each concrete refactoring action needs to implement the abstract
 * `createRefactoring` method which instantiates a ScalaIdeRefactoring.
 */
trait RefactoringAction extends ActionAdapter {

  /**
   * This factory method needs to be implemented by subclasses to
   * construct an appropriate ScalaIdeRefactoring instance.
   */
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile): ScalaIdeRefactoring

  /**
   * Creates a ScalaRefactoringWizard for this ScalaIdeRefactoring instance.
   *
   * @param refactoring An optional ScalaIdeRefactoring instance.
   * @return When None is passed, an instance of ErrorRefactoringWizard is returned.
   */
  def createWizardForRefactoring(refactoring: Option[ScalaIdeRefactoring]): RefactoringWizard = {
    refactoring map (new ScalaRefactoringWizard(_)) getOrElse (new ErrorRefactoringWizard("Error, not a text editor."))
  }

  /**
   * Creates a ScalaIdeRefactoring for the current active editor and selection. If no appropriate
   * editor can be found of if it's not possible to create a ScalaSourceFile for this editor,
   * None is returned.
   */
  def createScalaIdeRefactoringForCurrentEditorAndSelection(): Option[ScalaIdeRefactoring] = {
    import EditorHelpers._

    withScalaSourceFileAndSelection { (scalaFile, selection) =>
      Some(createRefactoring(selection.getOffset, selection.getOffset + selection.getLength, scalaFile))
    }
  }

  /**
   * Runs the given wizard in a RefactoringWizardOpenOperation and the current shell.
   *
   * Some of the refactoring implementations don't run in a wizard but make use of the
   * linked mode ui. These refactorings call `enterLinkedModeUi` directly.
   */
  def runRefactoring(wizard: RefactoringWizard, shell: Shell) {
    (new RefactoringWizardOpenOperation(wizard)).run(shell, "Scala Refactoring")
  }

  def run(action: IAction) {
    val shell = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell
    runRefactoring(createWizardForRefactoring(createScalaIdeRefactoringForCurrentEditorAndSelection()), shell)
  }
}
