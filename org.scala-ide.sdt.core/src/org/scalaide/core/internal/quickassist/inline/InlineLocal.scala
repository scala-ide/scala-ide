package org.scalaide.core.internal.quickassist
package inline

import org.scalaide.core.internal.statistics.Features.InlineLocalValue
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist

object InlineLocalProposal
  extends ProposalRefactoringHandlerAdapter(
      InlineLocalValue, new org.scalaide.refactoring.internal.InlineLocal, InlineLocalValue.description)

class InlineLocal extends QuickAssist {
  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] =
    if (InlineLocalProposal.isValidProposal) Seq(InlineLocalProposal) else Seq()
}
