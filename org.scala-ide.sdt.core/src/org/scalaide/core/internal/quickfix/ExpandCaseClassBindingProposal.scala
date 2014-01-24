package scala.tools.eclipse.quickfix

import scala.tools.eclipse.refactoring.ExpandCaseClassBindingAction

object ExpandCaseClassBindingProposal
  extends ProposalRefactoringActionAdapter(
      new ExpandCaseClassBindingAction, "Expand case class binding")