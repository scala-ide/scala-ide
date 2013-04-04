package scala.tools.eclipse.quickfix

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point

abstract class BasicCompletionProposal(relevance: Int, displayString: String, image: Image = null) extends IJavaCompletionProposal {
  override def getRelevance = relevance
  override def getDisplayString(): String = displayString
  override def getSelection(document: IDocument): Point = null
  override def getAdditionalProposalInfo(): String = null
  override def getImage(): Image = image
  override def getContextInformation: IContextInformation = null
}