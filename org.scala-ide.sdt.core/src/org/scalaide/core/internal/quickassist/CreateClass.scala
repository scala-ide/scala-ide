package org.scalaide.core.internal.quickassist

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.ui.JavaUI
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.util.eclipse.EditorUtils

class CreateClass extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    val editor = JavaUI.openInEditor(ctx.sourceFile)
    val assists = for {
      location <- ctx.problemLocations
      (ann, pos) <- EditorUtils.getAnnotationsAtOffset(editor, location.getOffset)
    } yield suggestCreateClassFix(ctx.sourceFile, ann.getText)

    assists.flatten
  }

  private def suggestCreateClassFix(compilationUnit : ICompilationUnit, problemMessage : String) =
    matchTypeNotFound(problemMessage, missingType => List(CreateClassProposal(missingType, compilationUnit)))
}
