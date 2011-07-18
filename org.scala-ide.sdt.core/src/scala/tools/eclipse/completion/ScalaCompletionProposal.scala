package scala.tools.eclipse.completion

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.contentassist.{ ICompletionProposalExtension, IContextInformation }
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.ITextViewer

private[completion] class ScalaCompletionProposal(startPos: Int,
  completion: String,        // the string to be inserted in the document
  display: String,           // the display string in the completion list
  tooltip: String,           // tooltop info showed after a completion has been selected
  additionalInfo: String,    // info displayed on the right of the current completion selection
  relevance: Int,
  image: Image,
  selectionProvider: ISelectionProvider)
  extends IJavaCompletionProposal
  with ICompletionProposalExtension {

  def getRelevance() = relevance
  def getImage() = image
  def getContextInformation(): IContextInformation =
    if (tooltip.size > 0)
      new ScalaContextInformation(display, tooltip, image)
    else null

  def getDisplayString() = display
  def getAdditionalProposalInfo() = additionalInfo
  def getSelection(d: IDocument) = null
  def apply(d: IDocument) { throw new IllegalStateException("Shouldn't be called") }

  def apply(d: IDocument, trigger: Char, offset: Int) {
    d.replace(startPos, offset - startPos, completion)
    selectionProvider.setSelection(new TextSelection(startPos + completion.length, 0))
    selectionProvider match {
      case viewer: ITextViewer if completion.endsWith("()") =>
        viewer.getTextWidget().setCaretOffset(startPos + completion.length - 1)
      case _ => () 
    }
  }
  def getTriggerCharacters = null
  def getContextInformationPosition = 0
  def isValidFor(d: IDocument, pos: Int) = 
    prefixMatches(completion.toArray, 
        d.get.substring(startPos, pos).toArray)
} 

