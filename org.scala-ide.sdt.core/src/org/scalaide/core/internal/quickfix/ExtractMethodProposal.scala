package org.scalaide.core.internal.quickfix

import org.scalaide.refactoring.internal.ExtractMethod

object ExtractMethodProposal
  extends ProposalRefactoringHandlerAdapter(
      new ExtractMethod, "Extract method")
