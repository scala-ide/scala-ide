package scala.tools.eclipse
package quickfix

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jdt.ui.{ISharedImages, JavaUI}
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.jface.text.{TextUtilities, IDocument}
import org.eclipse.swt.graphics.{Point, Image}

import scala.tools.eclipse.refactoring.EditorHelpers
import scala.tools.eclipse.util.HasLogger
import scala.tools.refactoring.implementations.AddImportStatement

case class ImportCompletionProposal(val importName: String) extends IJavaCompletionProposal with HasLogger {
  
  /**
   * Fixed relevance at 100 for now.
   */
  def getRelevance = 100
  
  /**
   * Inserts the proposed completion into the given document.
   *
   * @param document the document into which to insert the proposed completion
   */
  def apply(document: IDocument) {
    // First, try to insert with an AST transformation, if that fails, use the (old) method
    try {
      applyByASTTransformation(document)
    } catch {
      case t => {
        logger.error("failed to update import by AST transformation, fallback to text implementation", t)
        applyByTextTransformation(document)
      }
    }
  }
  
  /**
   * Inserts the proposed completion into the given document.
   *
   * @param document the document into which to insert the proposed completion
   */
  private def applyByASTTransformation(document: IDocument) {
    
    EditorHelpers.withScalaFileAndSelection { (scalaSourceFile, textSelection) =>
    
      val changes = scalaSourceFile.withSourceFile { (sourceFile, compiler) =>
       
         val r = new compiler.Response[compiler.Tree]
         compiler.askLoadedTyped(sourceFile, r)
         (r.get match {
           case Right(error) =>
             logger.error(error)
             None
           case _ =>
             compiler.askOption {() =>
               val refactoring = new AddImportStatement { val global = compiler }
               refactoring.addImport(scalaSourceFile.file, importName)
             }
         }) getOrElse Nil
        
      }(Nil)
      
      EditorHelpers.applyChangesToFileWhileKeepingSelection(document, textSelection, scalaSourceFile.file, changes)
      
      None
    }
  }
  
  /**
   * Inserts the proposed completion into the given document. (text based transformation)
   *
   * @param document the document into which to insert the proposed completion
   */
  private def applyByTextTransformation(document: IDocument) {
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
  def getImage(): Image = JavaUI.getSharedImages().getImage(ISharedImages.IMG_OBJS_IMPDECL)

  
  /**
   * Returns optional context information associated with this proposal.
   * The context information will automatically be shown if the proposal
   * has been applied.
   *
   * @return the context information for this proposal or <code>null</code>
   */
  def getContextInformation: IContextInformation = null
}
