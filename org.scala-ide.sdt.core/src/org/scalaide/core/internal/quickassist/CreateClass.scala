package org.scalaide.core.internal.quickassist

import org.eclipse.jdt.core.ICompilationUnit
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist

class CreateClass extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    val cu = ctx.icu.asInstanceOf[ICompilationUnit]
    ctx.problemLocations flatMap { location =>
      matchTypeNotFound(location.annotation.getText, missingType => List(CreateClassProposal(missingType, cu)))
    }
  }
}
