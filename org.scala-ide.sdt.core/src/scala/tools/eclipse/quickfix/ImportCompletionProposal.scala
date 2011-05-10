package scala.tools.eclipse
package quickfix

import util.FileUtils
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jdt.ui.ISharedImages
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
import scala.tools.eclipse.util.IDESettings
import org.eclipse.text.edits.MultiTextEdit
import org.eclipse.text.edits.RangeMarker

object ImportCompletionProposal {
  private val Strategies_Ast = "by ast transformation"
  private val Strategies_Text = "by text transformation"
  private val Strategies_AstThenText = "by ast and fallback to text if failed"
    
  val strategies = List(Strategies_AstThenText, Strategies_Ast, Strategies_Text)
}

case class ImportCompletionProposal(val importName: String) extends IJavaCompletionProposal {
  
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
    IDESettings.quickfixImportByText.value match {
      case ImportCompletionProposal.Strategies_Text => applyByTextTransfo(document)
      case ImportCompletionProposal.Strategies_Ast => applyByASTTransfo(document)
      case ImportCompletionProposal.Strategies_AstThenText => try {
        applyByASTTransfo(document)
      } catch {
        case t => {
          ScalaPlugin.plugin.logWarning("failed to update import by AST transformation, fallback to text implementation", Some(t))
          applyByTextTransfo(document)
        }
      }
    }
  }
  
  /**
   * Inserts the proposed completion into the given document.
   *
   * @param document the document into which to insert the proposed completion
   */
  private def applyByASTTransfo(document : IDocument) : Unit = {
    
    withScalaFileAndSelection { (scalaSourceFile, textSelection) =>
    
      val changes = scalaSourceFile.withSourceFile { (sourceFile, compiler) =>
            
        val refactoring = new AddImportStatement {
          val global = compiler
          
          val selection = {
            val start = textSelection.getOffset
            val end = start + textSelection.getLength
            val file = scalaSourceFile.file
            // start and end are not yet used
            new FileSelection(file, start, end)
          }
        }
       
        refactoring.addImport(refactoring.selection, importName)
      }(Nil)
      
      applyChangesToFileWhileKeepingSelection(document, textSelection, scalaSourceFile.file, changes)
      
      None
    }
  }
  
  /**
   * Inserts the proposed completion into the given document. (text based transformation)
   *
   * @param document the document into which to insert the proposed completion
   */
  private def applyByTextTransfo(document : IDocument) : Unit = {
    val lineDelimiter = TextUtilities.getDefaultLineDelimiter(document)

    // Find the package declaration
    val text = document.get
    var insertIndex = 0
    val packageIndex = text.indexOf("package", insertIndex)
    var preInsert = "" 
    
    if (packageIndex != -1) {
      // Insert on the line after the last package declaration, with a line of whitespace first if needed
      var nextLineIndex = text.indexOf(lineDelimiter, packageIndex) + 1
      var nextLineEndIndex = text.indexOf(lineDelimiter, nextLineIndex)
      var nextLine = text.substring(nextLineIndex, nextLineEndIndex).trim()
      
      // scan to see if package declaration is not multi-line
      while (nextLine.startsWith("package")) {
        nextLineIndex = text.indexOf(lineDelimiter, nextLineIndex) + 1
        nextLineEndIndex = text.indexOf(lineDelimiter, nextLineIndex)
        nextLine = text.substring(nextLineIndex, nextLineEndIndex).trim()
      }

      // Get the next line to see if it is already whitespace
      if (nextLine.trim() == "") {
        // This is a whitespace line, add the import here
        insertIndex = nextLineEndIndex + 1
      } else {
        // Need to insert whitespace after the package declaration and insert
        preInsert = lineDelimiter
        insertIndex = nextLineIndex
      }
    } else {
      // Insert at the top of the file
      insertIndex = 0
    }
    
    // Insert the import as the third line in the file... RISKY AS HELL :D
    document.replace(insertIndex, 0, preInsert + "import " + importName + lineDelimiter);
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
  def getSelection(document: IDocument): Point = null
  

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
  def getAdditionalProposalInfo(): String = null
  

  /**
   * Returns the string to be displayed in the list of completion proposals.
   *
   * @return the string to be displayed
   * 
   * @see ICompletionProposalExtension6#getStyledDisplayString()
   */
  def getDisplayString(): String = "Import " + importName
    

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
  def getContextInformation: IContextInformation = null
}
