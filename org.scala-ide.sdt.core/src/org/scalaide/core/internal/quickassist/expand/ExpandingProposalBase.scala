package org.scalaide.core.internal.quickassist
package expand

import org.eclipse.jface.text.IDocument
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.ui.internal.editor.decorators.implicits.ImplicitHighlightingPresenter
import org.scalaide.core.internal.statistics.Features.Feature

class ExpandingProposalBase(feature: Feature, msg: String, displayString: String, offset: Int, length: Int)
  extends BasicCompletionProposal(feature, relevance = RelevanceValues.ExpandingProposalBase, displayString = displayString + msg) {

  /**
   * Inserts the proposed completion into the given document.
   *
   * @param document the document into which to insert the proposed completion
   */
  override def applyProposal(document: IDocument): Unit = {
    // We extract the replacement string from the marker's message.
    val ReplacementExtractor = s"(?s).*${ImplicitHighlightingPresenter.DisplayStringSeparator}(.*)".r
    val ReplacementExtractor(replacement) = msg
    document.replace(offset, length, replacement)
  }

}
