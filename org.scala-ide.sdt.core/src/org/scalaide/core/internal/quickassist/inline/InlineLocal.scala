package org.scalaide.core.internal.quickassist
package inline

import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist

object InlineLocalProposal
  extends ProposalRefactoringHandlerAdapter(
      new org.scalaide.refactoring.internal.InlineLocal, "Inline local value")

class InlineLocal extends QuickAssist {
  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] =
    if (InlineLocalProposal.isValidProposal) Seq(InlineLocalProposal) else Seq()
}
