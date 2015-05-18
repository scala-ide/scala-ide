package org.scalaide.core.internal.quickassist

import scala.tools.refactoring.implementations.AddImportStatement

import org.eclipse.jdt.ui.ISharedImages
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextUtilities
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.internal.statistics.Features.ImportMissingMember
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.util.internal.eclipse.TextEditUtils

case class ImportCompletionProposal(importName: String)
    extends BasicCompletionProposal(
        ImportMissingMember,
        relevance = RelevanceValues.ImportCompletionProposal,
        displayString = s"Import $importName",
        image = JavaUI.getSharedImages().getImage(ISharedImages.IMG_OBJS_IMPDECL))
    with HasLogger {

  /**
   * Inserts the proposed completion into the given document.
   *
   * @param document the document into which to insert the proposed completion
   */
  override def applyProposal(document: IDocument): Unit = {
    // First, try to insert with an AST transformation, if that fails, use the (old) method
    try {
      applyByASTTransformation(document)
    } catch {
      case t: Exception => {
        eclipseLog.error("failed to update import by AST transformation, fallback to text implementation", t)
        applyByTextTransformation(document)
      }
    }
  }

  /**
   * Inserts the proposed completion into the given document.
   *
   * @param document the document into which to insert the proposed completion
   */
  private def applyByASTTransformation(document: IDocument): Unit = {

    EditorUtils.withScalaFileAndSelection { (scalaSourceFile, textSelection) =>

      val changes = scalaSourceFile.withSourceFile { (sourceFile, compiler) =>

         val r = compiler.askLoadedTyped(sourceFile, false)
         (r.get match {
           case Right(error) =>
             eclipseLog.error(error)
             None
           case _ =>
             compiler.asyncExec {
               val refactoring = new AddImportStatement { val global = compiler }
               refactoring.addImport(scalaSourceFile.file, importName)
             } getOption()
         }) getOrElse Nil

      } getOrElse (Nil)

      TextEditUtils.applyChangesToFileWhileKeepingSelection(document, textSelection, scalaSourceFile.file, changes)

      None
    }
  }

  /**
   * Inserts the proposed completion into the given document. (text based transformation)
   *
   * @param document the document into which to insert the proposed completion
   */
  private def applyByTextTransformation(document: IDocument): Unit = {
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
}
