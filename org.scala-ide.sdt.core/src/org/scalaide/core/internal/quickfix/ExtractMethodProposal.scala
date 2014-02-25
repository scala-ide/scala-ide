package org.scalaide.core.internal.quickfix

import org.scalaide.refactoring.internal.ExtractMethodAction

object ExtractMethodProposal
  extends ProposalRefactoringActionAdapter(
      new ExtractMethodAction, "Extract method")
