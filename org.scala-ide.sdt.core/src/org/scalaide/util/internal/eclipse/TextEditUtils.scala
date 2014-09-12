package org.scalaide.util.internal.eclipse

import org.eclipse.core.resources.IFile
import org.eclipse.text.edits.MultiTextEdit
import org.eclipse.text.edits.ReplaceEdit
import org.eclipse.ltk.core.refactoring.TextFileChange
import scala.tools.refactoring.common.TextChange
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.IDocument
import scala.reflect.io.AbstractFile
import org.eclipse.text.edits.RangeMarker
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.IRegion
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.text.edits.UndoEdit

object TextEditUtils {

  def applyRefactoringChangeToEditor(change: TextChange, editor: ITextEditor): UndoEdit = {
    val edit = new ReplaceEdit(change.from, change.to - change.from, change.text)
    val document = editor.getDocumentProvider.getDocument(editor.getEditorInput)
    edit.apply(document)
  }

  /** Creates a `TextFileChange` which always contains a `MultiTextEdit`. */
  def createTextFileChange(file: IFile, fileChanges: List[TextChange], saveAfter: Boolean = true): TextFileChange = {
    new TextFileChange(file.getName(), file) {

      val fileChangeRootEdit = new MultiTextEdit

      fileChanges map { change =>
        new ReplaceEdit(change.from, change.to - change.from, change.text)
      } foreach fileChangeRootEdit.addChild

      if (saveAfter) setSaveMode(TextFileChange.LEAVE_DIRTY)
      setEdit(fileChangeRootEdit)
    }
  }

  /**
   * Applies a list of refactoring changes to a document and its underlying file.
   * In contrast to `applyChangesToFileWhileKeepingSelection` this method is UI
   * independent and therefore does not restore the correct selection in the editor.
   * Instead it returns the new selection which then can be handled afterwards.
   *
   * `None` is returned if an error occurs while writing to the underlying file.
   *
   * @param document The document the changes are applied to.
   * @param textSelection The currently selected area of the document.
   * @param file The file that we're currently editing (the document alone isn't enough because we need to get an IFile).
   * @param changes The changes that should be applied.
   * @param saveAfter Whether files should be saved after changes
   */
  def applyChangesToFile(
      document: IDocument,
      textSelection: ITextSelection,
      file: AbstractFile,
      changes: List[TextChange],
      saveAfter: Boolean = true): Option[ITextSelection] = {

    FileUtils.toIFile(file) map { f =>
      createTextFileChange(f, changes, saveAfter).getEdit match {
        // we know that it is a MultiTextEdit because we created it above
        case edit: MultiTextEdit =>
          applyMultiTextEdit(document, textSelection, edit)
      }
    }
  }

  /**
   * Applies a list of refactoring changes to a document. The current selection
   * (or just the caret position) is tracked and restored after applying the changes.
   *
   * In contrast to `applyChangesToFile` this method is UI dependent.
   *
   * @param document The document the changes are applied to.
   * @param textSelection The currently selected area of the document.
   * @param file The file that we're currently editing (the document alone isn't enough because we need to get an IFile).
   * @param changes The changes that should be applied.
   * @param saveAfter Whether files should be saved after changes
   */
  def applyChangesToFileWhileKeepingSelection(
      document: IDocument,
      textSelection: ITextSelection,
      file: AbstractFile,
      changes: List[TextChange],
      saveAfter: Boolean = true): Unit = {

    applyChangesToFile(document, textSelection, file, changes, saveAfter) foreach { selection =>
      EditorUtils.doWithCurrentEditor { _.selectAndReveal(selection.getOffset(), selection.getLength()) }
    }
  }

  /**
   * Non UI logic that applies a `MultiTextEdit` and therefore the underlying document.
   * Returns a new text selection that describes the selection after the edit is applied.
   */
  def applyMultiTextEdit(document: IDocument, textSelection: ITextSelection, edit: MultiTextEdit): ITextSelection = {
    def selectionIsInManipulatedRegion(region: IRegion): Boolean = {
      val regionStart = region.getOffset
      val regionEnd = regionStart + region.getLength()
      val selectionStart = textSelection.getOffset()
      val selectionEnd = selectionStart + textSelection.getLength()

      selectionStart >= regionStart && selectionEnd <= regionEnd
    }

    val selectionCannotBeRetained = edit.getChildren map (_.getRegion) exists selectionIsInManipulatedRegion

    if (selectionCannotBeRetained) {
      // the selection overlaps the selected region, so we are on
      // our own in trying to the preserve the user's selection.
      if (edit.getOffset > textSelection.getOffset) {
        edit.apply(document)
        // if the edit starts after the start of the selection,
        // we just keep the current selection
        new TextSelection(document, textSelection.getOffset, textSelection.getLength)
      } else {
        // if the edit starts before the selection, we keep the
        // selection relative to the end of the document.
        val originalLength = document.getLength
        edit.apply(document)
        val modifiedLength = document.getLength
        new TextSelection(document, textSelection.getOffset + (modifiedLength - originalLength), textSelection.getLength())
      }

    } else {
      // Otherwise, we can track the selection and restore it after the refactoring.
      val currentPosition = new RangeMarker(textSelection.getOffset, textSelection.getLength)
      edit.addChild(currentPosition)
      edit.apply(document)
      new TextSelection(document, currentPosition.getOffset, currentPosition.getLength)
    }
  }

}