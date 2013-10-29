package scala.tools.eclipse.quickfix
package explicit

import org.eclipse.jface.text.IDocument

/** A generic completion proposal that inserts text at a given offset.
 */
class ExpandText(relevance: Int, displayString: String, tpt: String, offset: Int) extends BasicCompletionProposal(relevance, displayString) {
  def apply(document: IDocument): Unit = {
    document.replace(offset, 0, tpt)
  }

  override def toString =
    s"ExpandText(tpt = $tpt, offset = $offset)"
}