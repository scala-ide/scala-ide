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

abstract class ProposalRefactoringActionAdapter(
    val action: ActionAdapter, 
    relevance: Int, 
    displayString: String)
	extends IJavaCompletionProposal {
  
  def apply(document: IDocument): Unit = {
    action.run(null)
  }
  
  override def getRelevance = relevance
  def getDisplayString(): String = displayString
  def getSelection(document: IDocument): Point = null
  def getAdditionalProposalInfo(): String = null
  def getImage(): Image = null
  def getContextInformation: IContextInformation = null

  def isValidProposal : Boolean = {
    val ra = action match {
      case refactoringAction: RefactoringAction => refactoringAction
      case renameAction : RenameAction => renameAction.getRenameAction
    }
    ra.createScalaIdeRefactoringForCurrentEditorAndSelection match {
    	case Some(refactoring) => !refactoring.checkInitialConditions(null).hasWarning
    	case None	=> false
    }
  }
  
}