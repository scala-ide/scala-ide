package org.scalaide.core.internal.quickassist
package expand

import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist

object ExpandCaseClassBindingProposal
  extends ProposalRefactoringHandlerAdapter(
      new org.scalaide.refactoring.internal.ExpandCaseClassBinding, "Expand case class binding")

class ExpandCaseClassBinding extends QuickAssist {
  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] =
    if (ExpandCaseClassBindingProposal.isValidProposal) Seq(ExpandCaseClassBindingProposal) else Seq()
}
