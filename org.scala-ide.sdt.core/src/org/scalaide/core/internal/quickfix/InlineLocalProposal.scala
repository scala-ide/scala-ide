package org.scalaide.core.internal.quickfix

import org.scalaide.refactoring.internal.InlineLocalAction

object InlineLocalProposal
  extends ProposalRefactoringActionAdapter(
      new InlineLocalAction, "Inline local value")
