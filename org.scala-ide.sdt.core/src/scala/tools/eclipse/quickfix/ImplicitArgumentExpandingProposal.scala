package scala.tools.eclipse.quickfix

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.jface.text.TextUtilities
import org.eclipse.jdt.ui.text.java.IProblemLocation

class ImplicitArgumentExpandingProposal(val s: String, val loc: IProblemLocation) extends IJavaCompletionProposal {
  /**
   * Fixed relevance at 100 for now.
   */
  def getRelevance = 100
  
  
  /**
   * Inserts the proposed completion into the given document.
   *
   * @param document the document into which to insert the proposed completion
   */
  def apply(document : IDocument) : Unit = {
	val startInd = s.indexOf("=>")
    document.replace(loc.getOffset(), loc.getLength(), s.substring(startInd+3));
  }
  

  def getSelection(document : IDocument) : Point = null
  def getAdditionalProposalInfo() : String = null
  def getDisplayString() : String = "Explicitly inline the implicit arguments: " + s
  def getImage() : Image = null
  def getContextInformation : IContextInformation = null

}