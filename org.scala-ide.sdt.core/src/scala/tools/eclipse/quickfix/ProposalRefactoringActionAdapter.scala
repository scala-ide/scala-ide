package scala.tools.eclipse.quickfix

import scala.tools.eclipse.completion.RelevanceValues
import scala.tools.eclipse.refactoring.ActionAdapter
import scala.tools.eclipse.refactoring.RefactoringAction
import scala.tools.eclipse.refactoring.rename.RenameAction

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jface.text.IDocument

abstract class ProposalRefactoringActionAdapter(
    action: ActionAdapter,
    displayString: String,
    relevance: Int = RelevanceValues.ProposalRefactoringActionAdapter)
  extends BasicCompletionProposal(relevance, displayString) {

  override def apply(document: IDocument): Unit = {
    // document is not used because the refactoring actions use the current editor
    // TODO not sure if this null here is very safe
    action.run(null)
  }

  def isValidProposal : Boolean = {
    val ra = action match {
      case refactoringAction: RefactoringAction => refactoringAction
      case renameAction : RenameAction => renameAction.getRenameAction
    }
    ra.createScalaIdeRefactoringForCurrentEditorAndSelection match {
      // TODO not sure if this null here is very safe
      case Some(refactoring) => !refactoring.checkInitialConditions(new NullProgressMonitor).hasWarning
      case None  => false
    }
  }

}