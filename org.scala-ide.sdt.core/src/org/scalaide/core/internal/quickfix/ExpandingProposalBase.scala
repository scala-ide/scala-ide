package org.scalaide.core.internal.quickfix

import org.scalaide.core.completion.RelevanceValues
import org.scalaide.ui.internal.editor.decorators.implicits.ImplicitHighlightingPresenter
import org.scalaide.util.internal.Utils
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.Position

class ExpandingProposalBase(msg: String, displayString: String, pos: Position)
  extends BasicCompletionProposal(relevance = RelevanceValues.ExpandingProposalBase, displayString = displayString + msg) {

  /**
   * Inserts the proposed completion into the given document.
   *
   * @param document the document into which to insert the proposed completion
   */
  def apply(document: IDocument): Unit = {
    // We extract the replacement string from the marker's message.
    val ReplacementExtractor = s"(?s).*${ImplicitHighlightingPresenter.DisplayStringSeparator}(.*)".r
    val ReplacementExtractor(replacement) = msg
    document.replace(pos.getOffset(), pos.getLength(), replacement)
  }

}
