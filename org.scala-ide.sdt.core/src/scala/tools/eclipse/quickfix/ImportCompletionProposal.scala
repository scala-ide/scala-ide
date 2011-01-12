package scala.tools.eclipse.quickfix

import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jdt.ui.ISharedImages
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.text.edits.ReplaceEdit
import org.eclipse.text.edits.MultiTextEdit
import org.eclipse.ltk.core.refactoring.TextFileChange
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.util.EclipseResource
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.jface.text.TextUtilities
import scala.tools.eclipse.refactoring.EditorHelpers._
import scala.tools.refactoring.implementations.AddImportStatement

case class ImportCompletionProposal(val importName : String) extends IJavaCompletionProposal {
  
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
    
    withScalaFileAndSelection { (scalaSourceFile, iTextSelection) =>
    
      val changes = scalaSourceFile.withCompilerResult { crh =>
            
        val refactoring = new AddImportStatement {
          val global = crh.compiler
          
          val selection = {
            val start = iTextSelection.getOffset
            val end = start + iTextSelection.getLength
            val file = crh.sourceFile.file
            // start and end are not yet used
            new FileSelection(file, start, end)
          }
        }
       
        refactoring.addImport(refactoring.selection, importName)
      }
      
      scalaSourceFile.file match {
        case EclipseResource(file: IFile) => 
          val textFileChange = createTextFileChange(file, changes)
          textFileChange.getEdit.apply(document)
      }
      
      None
    }
  }
  
  /**
   * Returns the new selection after the proposal has been applied to
   * the given document in absolute document coordinates. If it returns
   * <code>null</code>, no new selection is set.
   *
   * A document change can trigger other document changes, which have
   * to be taken into account when calculating the new selection. Typically,
   * this would be done by installing a document listener or by using a
   * document position during {@link #apply(IDocument)}.
   *
   * @param document the document into which the proposed completion has been inserted
   * @return the new selection in absolute document coordinates
   */
  def getSelection(document : IDocument) : Point = null
  

  /**
   * Returns optional additional information about the proposal. The additional information will
   * be presented to assist the user in deciding if the selected proposal is the desired choice.
   * <p>
   * If {@link ICompletionProposalExtension5} is implemented, this method should not be called any
   * longer. This method may be deprecated in a future release.
   * </p>
   *
   * @return the additional information or <code>null</code>
   */
  def getAdditionalProposalInfo() : String = null
  

  /**
   * Returns the string to be displayed in the list of completion proposals.
   *
   * @return the string to be displayed
   * 
   * @see ICompletionProposalExtension6#getStyledDisplayString()
   */
  def getDisplayString() : String = "Import " + importName
    

  /**
   * Returns the image to be displayed in the list of completion proposals.
   * The image would typically be shown to the left of the display string.
   *
   * @return the image to be shown or <code>null</code> if no image is desired
   */
  def getImage() : Image = JavaUI.getSharedImages().getImage(ISharedImages.IMG_OBJS_IMPDECL)

  
  /**
   * Returns optional context information associated with this proposal.
   * The context information will automatically be shown if the proposal
   * has been applied.
   *
   * @return the context information for this proposal or <code>null</code>
   */
  def getContextInformation : IContextInformation = null
}
