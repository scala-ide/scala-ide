package scala.tools.eclipse.completion

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.contentassist.{ ICompletionProposalExtension, IContextInformation }
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jface.text.TextSelection

private[completion] class ScalaCompletionProposal(startPos: Int,
  completion: String,
  display: String,
  contextName: String,
  container: String,
  relevance: Int,
  image: Image,
  selectionProvider: ISelectionProvider)
  extends IJavaCompletionProposal
  with ICompletionProposalExtension {

  def getRelevance() = relevance
  def getImage() = image
  def getContextInformation(): IContextInformation =
    if (contextName.size > 0)
      new ScalaContextInformation(display, contextName, image)
    else null

  def getDisplayString() = display
  def getAdditionalProposalInfo() = container
  def getSelection(d: IDocument) = null
  def apply(d: IDocument) { throw new IllegalStateException("Shouldn't be called") }

  def apply(d: IDocument, trigger: Char, offset: Int) {
    d.replace(startPos, offset - startPos, completion)
    selectionProvider.setSelection(new TextSelection(startPos + completion.length, 0))
  }
  def getTriggerCharacters = null
  def getContextInformationPosition = 0
  def isValidFor(d: IDocument, pos: Int) = prefixMatches(completion.toArray, d.get.substring(startPos, pos).toArray)
} 

