package org.scalaide.core.internal.quickassist

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jface.text.IDocument
import org.scalaide.core.internal.statistics.Features.Feature
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.refactoring.internal.RefactoringExecutor
import org.scalaide.refactoring.internal.RefactoringHandler
import org.scalaide.refactoring.internal.rename.Rename

abstract class ProposalRefactoringHandlerAdapter(
    feature: Feature,
    handler: RefactoringHandler,
    displayString: String,
    relevance: Int = RelevanceValues.ProposalRefactoringHandlerAdapter)
  extends BasicCompletionProposal(feature, relevance, displayString) {

  override def applyProposal(document: IDocument): Unit =
    handler.perform()

  def isValidProposal: Boolean = {
    val ra = handler match {
      case r: Rename => r.getRenameRefactoring
      case r: RefactoringExecutor => r
    }
    ra.createScalaIdeRefactoringForCurrentEditorAndSelection exists {
      (refactoring) => !refactoring.checkInitialConditions(new NullProgressMonitor).hasWarning
    }
  }

}
