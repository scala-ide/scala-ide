package org.scalaide.core.internal.quickfix

import org.scalaide.refactoring.internal.ExtractLocal

object ExtractLocalProposal
  extends ProposalRefactoringHandlerAdapter(
      new ExtractLocal, "Extract to local value")
