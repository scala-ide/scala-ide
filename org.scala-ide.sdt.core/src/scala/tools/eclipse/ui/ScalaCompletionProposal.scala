package scala.tools.eclipse
package ui

import completion._

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.contentassist.{ ICompletionProposalExtension, IContextInformation }
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jdt.internal.ui.JavaPluginImages


/** A UI class for displaying completion proposals.
 * 
 *  It adds parenthesis at the end of a proposal if it has parameters, and places the caret
 *  between them.
 */
class ScalaCompletionProposal(proposal: CompletionProposal, selectionProvider: ISelectionProvider) 
    extends IJavaCompletionProposal with ICompletionProposalExtension {
  
  import proposal._
  import ScalaCompletionProposal._
  
  def getRelevance = relevance
  
  private lazy val image = {
    import MemberKind._
    
    kind match {
      case Def           => defImage
      case Class         => classImage
      case Trait         => traitImage
      case Package       => packageImage
      case PackageObject => packageObjectImage
      case Object        =>
        if (isJava) javaClassImage
        else objectImage
      case Type => typeImage
      case _    => valImage
    }
  }
  
  def getImage = image
  
  val completionString = if (hasArgs) completion + "()" else completion
  
  def getContextInformation(): IContextInformation =
    if (tooltip.size > 0)
      new ScalaContextInformation(display, tooltip, image)
    else null

  def getDisplayString() = display
  def getAdditionalProposalInfo() = additionalInfo
  def getSelection(d: IDocument) = null
  def apply(d: IDocument) { throw new IllegalStateException("Shouldn't be called") }

  def apply(d: IDocument, trigger: Char, offset: Int) {
    d.replace(startPos, offset - startPos, completionString)
    selectionProvider.setSelection(new TextSelection(startPos + completionString.length, 0))
    selectionProvider match {
      case viewer: ITextViewer if hasArgs =>
        viewer.getTextWidget().setCaretOffset(startPos + completionString.length - 1)
      case _ => () 
    }
  }
  def getTriggerCharacters = null
  def getContextInformationPosition = 0
  def isValidFor(d: IDocument, pos: Int) = 
    prefixMatches(completion.toArray, d.get.substring(startPos, pos).toArray)
}

object ScalaCompletionProposal {
  import ScalaImages._
  val defImage = PUBLIC_DEF.createImage()
  val classImage = SCALA_CLASS.createImage()
  val traitImage = SCALA_TRAIT.createImage()
  val objectImage = SCALA_OBJECT.createImage()
  val packageObjectImage = SCALA_PACKAGE_OBJECT.createImage()
  val typeImage = SCALA_TYPE.createImage()
  val valImage = PUBLIC_VAL.createImage()

  val javaInterfaceImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_INTERFACE)
  val javaClassImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_CLASS)
  val packageImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_PACKAGE)
  
  def apply(selectionProvider: ISelectionProvider)(proposal: CompletionProposal) = new ScalaCompletionProposal(proposal, selectionProvider)
}