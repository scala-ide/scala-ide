package org.scalaide.util.internal.eclipse

import scala.reflect.io.AbstractFile
import scala.tools.refactoring.common.TextChange

import org.eclipse.core.resources.IFile
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.TextSelection
import org.eclipse.ltk.core.refactoring.TextFileChange
import org.eclipse.text.edits.MultiTextEdit
import org.eclipse.text.edits.RangeMarker
import org.eclipse.text.edits.ReplaceEdit
import org.eclipse.text.edits.TextEdit
import org.eclipse.text.edits.UndoEdit
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.util.eclipse.FileUtils
import org.scalaide.util.eclipse.RegionUtils

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
      org.scalaide.util.eclipse.EditorUtils.doWithCurrentEditor { _.selectAndReveal(selection.getOffset(), selection.getLength()) }
    }
  }

  /**
   * Non UI logic that applies a `edit` to the underlying `document`.
   * `textSelection` is the selection that should be preserved by this method.
   *
   * Returns a new text selection that describes the selection after the edit is
   * applied.
   */
  def applyMultiTextEdit(document: IDocument, textSelection: ITextSelection, edit: MultiTextEdit): ITextSelection = {
    import RegionUtils._
    val selStart = textSelection.start
    val selLen = textSelection.length
    val selEnd = textSelection.end

    /**
     * Checks if the selection overlaps with `region`.
     */
    def selectionOverlapsRegion(region: IRegion): Boolean = {
      val rStart = region.start
      val rEnd = region.end

      !(selStart < rStart && selEnd < rStart || selStart > rEnd && selEnd > rEnd)
    }

    /**
     * Handles the case that the selection does not overlap with one of the
     * regions.
     */
    def handleNonOverlap = {
      val currentPosition = new RangeMarker(selStart, selLen)
      edit.addChild(currentPosition)
      edit.apply(document)
      new TextSelection(document, currentPosition.start, currentPosition.length)
    }

    /**
     * Handles the case that the selection overlaps with some of the regions. We
     * have to preserve the selection manually and can't rely on the behavior of
     * `MultiTextEdit`.
     */
    def handleOverlap(overlappingEdit: TextEdit) = {
      val (newOffset, newLen) = {
        val rStart = overlappingEdit.start
        val rLen = overlappingEdit.length
        val rEnd = overlappingEdit.end

        def offsetInIntersection = rLen-(selStart-rStart)

        /**
         * In an overlapping region we either have to expand or shrink the
         * selection. Furthermore, the selection needs only to be adjusted for
         * changes that happen before its position whereas the changes
         * afterwards don't affect its position. In case the selection
         * intersects with a changed region there is only a subset of the
         * whole region needed for which the selection needs to be moved
         * forwards or backwards. This subset is described by
         * `overlapToPreserve`.
         */
        def adjustOffset(overlapToPreserve: Int) = {
          val lenAfterSelection = edit.getChildren().collect {
            case e if e.start > selStart =>
              e match {
                case e: ReplaceEdit => e.length-e.getText().length
                case e => e.length
              }
          }.sum

          val originalLength = document.length
          edit.apply(document)
          val modifiedLength = document.length-originalLength
          selStart+modifiedLength+lenAfterSelection+overlapToPreserve
        }

        // ^ = selStart/selEnd, [ = rStart, ] = rEnd
        // Don't need to be handled here:
        // - case 1: ^  ^ [  ], ^ [  ]
        // - case 6: [  ] ^  ^, [  ] ^

        // case 2: ^ [ ^ ]
        if (selStart < rStart && selEnd < rEnd)
          (adjustOffset(0), selLen-(selEnd-rStart))
        // case 3: ^ [  ] ^
        else if (selStart < rStart && selEnd > rEnd) {
          val sub = overlappingEdit match {
            case e: ReplaceEdit => e.length-e.getText().length
            case e => e.length
          }
          (adjustOffset(0), selLen-sub)
        }
        // case 4: [^  ^], [ ^ ]
        else if (selStart < rEnd && selEnd < rEnd)
          (adjustOffset(offsetInIntersection), 0)
        // case 5: [ ^ ] ^
        else
          (adjustOffset(offsetInIntersection), selLen-(rEnd-selStart))
      }

      new TextSelection(document, newOffset, newLen)
    }

    val overlappingEdit = edit.getChildren().find(e => selectionOverlapsRegion(e.getRegion()))
    overlappingEdit map handleOverlap getOrElse handleNonOverlap
  }

}
