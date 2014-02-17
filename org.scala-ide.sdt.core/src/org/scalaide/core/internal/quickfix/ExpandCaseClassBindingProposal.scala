package org.scalaide.core.internal.quickfix

import org.scalaide.refactoring.internal.ExpandCaseClassBinding

object ExpandCaseClassBindingProposal
  extends ProposalRefactoringHandlerAdapter(
      new ExpandCaseClassBinding, "Expand case class binding")
