package org.scalaide.core.internal.quickfix

import org.scalaide.refactoring.internal.ExpandCaseClassBindingAction

object ExpandCaseClassBindingProposal
  extends ProposalRefactoringActionAdapter(
      new ExpandCaseClassBindingAction, "Expand case class binding")
