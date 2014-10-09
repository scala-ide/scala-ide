package org.scalaide.core.internal.quickassist
package changecase

import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist

class ChangeCase extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    ctx.problemLocations flatMap { location =>
      location.annotation.getText match {
        case ValueNotAMember(value, className) =>
          ChangeCaseProposal.createProposals(ctx.icu, location.offset, location.length, value)
        case ValueNotFoundError(member) =>
          ChangeCaseProposal.createProposalsWithCompletion(ctx.icu, location.offset, location.length, member)
        case _ =>
          Nil
      }
    }
  }
}
