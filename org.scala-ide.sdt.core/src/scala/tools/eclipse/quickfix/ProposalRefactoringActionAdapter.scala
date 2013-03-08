package scala.tools.eclipse.quickfix

import scala.tools.eclipse.refactoring.RefactoringAction
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import scala.tools.eclipse.refactoring.ActionAdapter
import scala.tools.eclipse.refactoring.rename.RenameAction
import scala.tools.eclipse.refactoring.ScalaIdeRefactoring
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.runtime.NullProgressMonitor

abstract class ProposalRefactoringActionAdapter(
    action: ActionAdapter,
    displayString: String,
    relevance: Int = 100)
  extends IJavaCompletionProposal {

  override def apply(document: IDocument): Unit = {
    // document is not used because the refactoring actions use the current editor
    // TODO not sure if this null here is very safe
    action.run(null)
  }

  override def getRelevance = relevance
  override def getDisplayString(): String = displayString
  override def getSelection(document: IDocument): Point = null
  override def getAdditionalProposalInfo(): String = null
  override def getImage(): Image = null
  override def getContextInformation: IContextInformation = null

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