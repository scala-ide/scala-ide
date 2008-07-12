package scala.tools.eclipse

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringSaveHelper;
import org.eclipse.jdt.internal.ui.refactoring.UserInterfaceStarter;
import org.eclipse.jdt.internal.ui.refactoring.reorg.RenameUserInterfaceManager;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ltk.core.refactoring.participants.RenameProcessor

 
import scala.tools.eclipse.javaelements.ScalaCompilationUnit

class RenameScalaFileAction extends IActionDelegate {
  def run(action : IAction ) {
    val window = JavaPlugin.getActiveWorkbenchWindow
    if (window != null) {
      val sel = window.getSelectionService.getSelection
      if (sel.isInstanceOf[IStructuredSelection]) {
        val resource = getResource(sel.asInstanceOf[IStructuredSelection])
        if (RefactoringAvailabilityTester.isRenameAvailable(resource))
          RefactoringExecutionStarter.startRenameResourceRefactoring(resource, window.getShell)
      }
    }
  }

  def selectionChanged(action : IAction, selection : ISelection) {}

  private def getResource(selection : IStructuredSelection) : IResource = {
    if (selection.size != 1)
      return null
    val first = selection.getFirstElement
    if (first.isInstanceOf[ScalaCompilationUnit])
      first.asInstanceOf[ScalaCompilationUnit].getResource
    else
      null
  }
}
