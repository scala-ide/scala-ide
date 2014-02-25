package org.scalaide.core.internal.quickfix

import org.scalaide.refactoring.internal.ExtractLocalAction

object ExtractLocalProposal
  extends ProposalRefactoringActionAdapter(
      new ExtractLocalAction, "Extract to local value")
