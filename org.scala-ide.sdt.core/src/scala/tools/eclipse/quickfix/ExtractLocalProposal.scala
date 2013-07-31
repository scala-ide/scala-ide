package scala.tools.eclipse.quickfix

import scala.tools.eclipse.refactoring.ExtractLocalAction

object ExtractLocalProposal
  extends ProposalRefactoringActionAdapter(
      new ExtractLocalAction, "Extract to local value")