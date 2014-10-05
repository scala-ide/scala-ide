package org.scalaide.core.internal.quickassist
package changecase

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.Position
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.util.eclipse.EditorUtils

class ChangeCase extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    val editor = JavaUI.openInEditor(ctx.sourceFile)
    val assists = for {
      location <- ctx.problemLocations
      (ann, pos) <- EditorUtils.getAnnotationsAtOffset(editor, location.getOffset)
    } yield suggestChangeMethodCase(ctx.sourceFile, ann.getText, pos)

    assists.flatten
  }

  private def suggestChangeMethodCase(cu: ICompilationUnit, problemMessage: String, pos: Position) = {
    problemMessage match {
      case ValueNotAMember(value, className) => ChangeCaseProposal.createProposals(cu, pos, value)
      case ValueNotFoundError(member)        => ChangeCaseProposal.createProposalsWithCompletion(cu, pos, member)
      case _                                 => List()
    }
  }
}
