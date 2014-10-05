package org.scalaide.core.internal.quickassist

import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist

class CreateClass extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] =
    ctx.problemLocations flatMap { location =>
      matchTypeNotFound(location.annotation.getText, missingType => List(CreateClassProposal(missingType, ctx.sourceFile)))
    }
}
