package scala.tools.eclipse.quickfix

import scala.tools.eclipse.refactoring.ExtractMethodAction

object ExtractMethodProposal
  extends ProposalRefactoringActionAdapter(
      new ExtractMethodAction, "Extract method")