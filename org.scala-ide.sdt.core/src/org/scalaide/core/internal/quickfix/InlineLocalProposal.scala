package scala.tools.eclipse.quickfix

import scala.tools.eclipse.refactoring.InlineLocalAction

object InlineLocalProposal
  extends ProposalRefactoringActionAdapter(
      new InlineLocalAction, "Inline local value")