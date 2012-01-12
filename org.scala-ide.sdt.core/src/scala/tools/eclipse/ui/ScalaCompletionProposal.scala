package scala.tools.eclipse
package ui

import completion._
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.contentassist.{ ICompletionProposalExtension, ICompletionProposalExtension6, IContextInformation }
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.{ISelectionProvider, StyledString}
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jdt.internal.ui.JavaPluginImages
import refactoring.EditorHelpers
import refactoring.EditorHelpers._
import scala.tools.refactoring.implementations.AddImportStatement
import scala.tools.refactoring.common.Change


/** A UI class for displaying completion proposals.
 * 
 *  It adds parenthesis at the end of a proposal if it has parameters, and places the caret
 *  between them.
 */
class ScalaCompletionProposal(proposal: CompletionProposal, selectionProvider: ISelectionProvider) 
    extends IJavaCompletionProposal with ICompletionProposalExtension with ICompletionProposalExtension6 {
  
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
  
  val completionString = if (hasArgs == HasArgs.NoArgs) completion else completion + "()"
  
  def getContextInformation(): IContextInformation =
    if (tooltip.size > 0)
      new ScalaContextInformation(display, tooltip, image)
    else null
 
  /**
   * A simple display string
   */
  def getDisplayString() = display
  
  /**
   * A display string with grayed out extra details
   */
  def getStyledDisplayString() : StyledString = {
       val styledString= new StyledString(display)
       if (displayDetail != null && displayDetail.size > 0)
         styledString.append(" - ", StyledString.QUALIFIER_STYLER).append(displayDetail, StyledString.QUALIFIER_STYLER)
      styledString
    }
  
  /**
   * Some additional info (like javadoc ...)
   */
  def getAdditionalProposalInfo() = null
  def getSelection(d: IDocument) = null
  def apply(d: IDocument) { throw new IllegalStateException("Shouldn't be called") }

  def apply(d: IDocument, trigger: Char, offset: Int) {

    withScalaFileAndSelection { (scalaSourceFile, textSelection) =>

      val completionChange = Change(scalaSourceFile.file, startPos, offset, completionString)
      
      val importStmt = if (needImport) { // add an import statement if required
        scalaSourceFile.withSourceFile { (_, compiler) =>
          val refactoring = new AddImportStatement { val global = compiler }
          refactoring.addImport(scalaSourceFile.file, fullyQualifiedName)
        }(Nil)
      } else {
        Nil
      }
      
      // Apply the two changes in one step, if done separately we would need an
      // another `waitLoadedType` to update the positions for the refactoring
      // to work properly.
      EditorHelpers.applyChangesToFileWhileKeepingSelection(
          d, textSelection, scalaSourceFile.file, completionChange :: importStmt)            
     
      None
    }
        
    selectionProvider match {
      case viewer: ITextViewer if hasArgs == HasArgs.NonEmptyArgs =>
        // obtain the relative offset in the screen (this is needed to correctly 
        // update the caret position when folded comments/imports/classes are
        // present in the source file.
        //
        // Mirko: It doesn't seem to be needed anymore since we use the 
        //        `applyChangesToFileWhileKeepingSelection`, but it would
        //        be better if someone else also checked this.
        //
        //val viewCaretOffset = viewer.getTextWidget().getCaretOffset()
        //viewer.getTextWidget().setCaretOffset(viewCaretOffset -1 )
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
