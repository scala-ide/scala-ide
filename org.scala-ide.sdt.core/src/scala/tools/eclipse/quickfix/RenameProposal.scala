package scala.tools.eclipse.quickfix

import scala.tools.eclipse.refactoring.rename.RenameAction

object RenameProposal 
	extends ProposalRefactoringActionAdapter(
	    new RenameAction, 100, "Rename value")