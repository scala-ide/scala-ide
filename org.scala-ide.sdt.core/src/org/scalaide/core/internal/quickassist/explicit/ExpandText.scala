package org.scalaide.core.internal.quickassist.explicit

import org.eclipse.jface.text.IDocument
import org.scalaide.core.internal.statistics.Features.Feature
import org.scalaide.core.quickassist.BasicCompletionProposal

/** A generic completion proposal that inserts text at a given offset.
 */
class ExpandText(feature: Feature, relevance: Int, displayString: String, tpt: String, offset: Int) extends BasicCompletionProposal(feature, relevance, displayString) {
  override def applyProposal(document: IDocument): Unit = {
    document.replace(offset, 0, tpt)
  }

  override def toString: String =
    s"ExpandText(tpt = $tpt, offset = $offset)"
}
