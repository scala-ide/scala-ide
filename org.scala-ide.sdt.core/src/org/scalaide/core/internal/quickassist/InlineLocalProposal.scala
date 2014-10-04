package org.scalaide.core.internal.quickassist

import org.scalaide.refactoring.internal.InlineLocal

object InlineLocalProposal
  extends ProposalRefactoringHandlerAdapter(
      new InlineLocal, "Inline local value")
