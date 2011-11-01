package scala.tools.eclipse
package quickfix

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.jface.text.TextUtilities
import org.eclipse.jdt.ui.text.java.IProblemLocation
import org.eclipse.jface.text.Position
import scala.util.matching.Regex
import scala.tools.eclipse.semantic.highlighting.SemanticHighlightingPresenter

class ExpandingProposalBase(msg: String, displayString: String, pos: Position) extends IJavaCompletionProposal {
  /**
   * Fixed relevance at 100 for now, this is the maximum value according to IJavaCompletionProposal.
   */
  override def getRelevance = 100

  /**
   * Inserts the proposed completion into the given document.
   *
   * @param document the document into which to insert the proposed completion
   */
  def apply(document: IDocument): Unit = {
    // We extract the replacement string from the marker's message.
    val ReplacementExtractor = new Regex(".*"+ SemanticHighlightingPresenter.DisplayStringSeparator +"(.*)")
    val ReplacementExtractor(replacement) = msg
    document.replace(pos.getOffset(), pos.getLength(), replacement);
  }

  def getSelection(document: IDocument): Point = null
  def getAdditionalProposalInfo(): String = null
  def getDisplayString(): String = displayString + msg
  def getImage(): Image = null
  def getContextInformation: IContextInformation = null
}