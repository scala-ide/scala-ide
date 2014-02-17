package org.scalaide.core.internal.quickfix

import org.scalaide.refactoring.internal.InlineLocal

object InlineLocalProposal
  extends ProposalRefactoringHandlerAdapter(
      new InlineLocal, "Inline local value")
