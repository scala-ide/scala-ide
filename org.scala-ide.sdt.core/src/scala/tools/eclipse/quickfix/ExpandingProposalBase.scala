package scala.tools.eclipse
package quickfix

import scala.tools.eclipse.completion.RelevanceValues
import scala.tools.eclipse.semantichighlighting.implicits.ImplicitHighlightingPresenter
import scala.tools.eclipse.util.Utils

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
    import Utils._
    // We extract the replacement string from the marker's message.
    val r"(?s).*${ImplicitHighlightingPresenter.DisplayStringSeparator}(.*)$replacement" = msg
    document.replace(pos.getOffset(), pos.getLength(), replacement);
  }

}