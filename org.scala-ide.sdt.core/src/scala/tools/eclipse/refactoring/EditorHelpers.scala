/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring

import org.eclipse.text.edits.RangeMarker
import util.FileUtils
import org.eclipse.jface.text.IRegion
import scala.tools.refactoring.common.Change
import scala.tools.nsc.io.AbstractFile
import org.eclipse.jface.text.IDocument
import org.eclipse.text.edits.ReplaceEdit
import org.eclipse.text.edits.MultiTextEdit
import org.eclipse.ltk.core.refactoring.TextFileChange
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.ui.IFileEditorInput
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.jface.text.ITextSelection
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.jface.text.link.LinkedModeModel
import org.eclipse.jface.text.link.LinkedPositionGroup
import org.eclipse.jface.text.link.LinkedPosition
import org.eclipse.jface.text.link.LinkedModeUI
import scala.tools.refactoring.common.TextChange
import scala.tools.eclipse.util.EditorUtils

object EditorHelpers {

  def activeWorkbenchWindow: Option[IWorkbenchWindow] = Option(PlatformUI.getWorkbench.getActiveWorkbenchWindow)
  def activePage(w: IWorkbenchWindow): Option[IWorkbenchPage] = Option(w.getActivePage)
  def activeEditor(p: IWorkbenchPage): Option[IEditorPart] = if(p.isEditorAreaVisible) Some(p.getActiveEditor) else None
  def textEditor(e: IEditorPart): Option[ISourceViewerEditor] = e match {case t: ISourceViewerEditor => Some(t) case _ => None}
  def file(e: ITextEditor): Option[IFile] = e.getEditorInput match {
    case f: IFileEditorInput =>
      Some(f.getFile)
    case _ =>
      None
  }
  def selection(e: ITextEditor): Option[ITextSelection] = e.getSelectionProvider.getSelection match {case s: ITextSelection => Some(s) case _ => None}

  def doWithCurrentEditor(block: ISourceViewerEditor => Unit) {
    withCurrentEditor { editor =>
      block(editor)
      None
    }
  }

  def withCurrentEditor[T](block: ISourceViewerEditor => Option[T]): Option[T] = {
    activeWorkbenchWindow flatMap {
      activePage(_)         flatMap {
        activeEditor(_)       flatMap {
          textEditor(_)         flatMap block
        }
      }
    }
  }

  def withCurrentScalaSourceFile[T](block: ScalaSourceFile => T): Option[T] = {
    withCurrentEditor { textEditor =>
      file(textEditor)      flatMap { file =>
        ScalaSourceFile.createFromPath(file.getFullPath.toString) map block
      }
    }
  }

  def withScalaFileAndSelection[T](block: (InteractiveCompilationUnit, ITextSelection) => Option[T]): Option[T] = {
    withCurrentEditor { textEditor =>
      EditorUtils.getEditorCompilationUnit(textEditor) flatMap { icu =>
        selection(textEditor) flatMap { selection =>
          block(icu, selection)
        }
      }
    }
  }

  def withScalaSourceFileAndSelection[T](block: (ScalaSourceFile, ITextSelection) => Option[T]): Option[T] = {
    withScalaFileAndSelection { (icu, selection) =>
      icu match {
        case ssf: ScalaSourceFile => block(ssf, selection)
        case _ => None
      }
    }
  }

  def createTextFileChange(file: IFile, fileChanges: List[TextChange]): TextFileChange = {
    new TextFileChange(file.getName(), file) {

      val fileChangeRootEdit = new MultiTextEdit

      fileChanges map { change =>
        new ReplaceEdit(change.from, change.to - change.from, change.text)
      } foreach fileChangeRootEdit.addChild

      setEdit(fileChangeRootEdit)
    }
  }

  /**
   * Applies a list of refactoring changes to a document. The current selection (or just the caret position)
   * is tracked and restored after applying the changes.
   *
   * @param document The document the changes are applied to.
   * @param textSelection The currently selected area of the document.
   * @param file The file that we're currently editing (the document alone isn't enough because we need to get an IFile).
   * @param changes The changes that should be applied.
   */
  def applyChangesToFileWhileKeepingSelection(document: IDocument, textSelection: ITextSelection, file: AbstractFile, changes: List[TextChange])  {

    def selectionIsInManipulatedRegion(region: IRegion): Boolean = {
      val (regionStart, regionEnd) = {
        (region.getOffset, region.getOffset + region.getLength)
      }
      val (selectionStart, selectionEnd) = {
        (textSelection.getOffset, textSelection.getOffset + textSelection.getLength)
      }
      selectionStart >= regionStart && selectionEnd <= regionEnd
    }

    FileUtils.toIFile(file) foreach { f =>
      createTextFileChange(f, changes).getEdit match {
        // we know that it is a MultiTextEdit because we created it above
        case edit: MultiTextEdit =>

          val selectionCannotBeRetained = edit.getChildren map (_.getRegion) exists selectionIsInManipulatedRegion

          val (selectionStart, selectionLength) = if(selectionCannotBeRetained) {
            // the selection overlaps the selected region, so we are on
            // our own in trying to the preserve the user's selection.
            if(edit.getOffset > textSelection.getOffset) {
              edit.apply(document)
              // if the edit starts after the start of the selection,
              // we just keep the current selection
              (textSelection.getOffset, textSelection.getLength)
            } else {
              // if the edit starts before the selection, we keep the
              // selection relative to the end of the document.
              val originalLength = document.getLength
              edit.apply(document)
              val modifiedLength = document.getLength
              (textSelection.getOffset + (modifiedLength - originalLength), textSelection.getLength)
            }

          } else {
            // Otherwise, we can track the selection and restore it after the refactoring.
            val currentPosition = new RangeMarker(textSelection.getOffset, textSelection.getLength)
            edit.addChild(currentPosition)
            edit.apply(document)
            (currentPosition.getOffset, currentPosition.getLength)
          }

          withCurrentEditor { editor =>
            editor.selectAndReveal(selectionStart, selectionLength)
            None
          }
      }
    }
  }

  def applyRefactoringChangeToEditor(change: TextChange, editor: ITextEditor) = {
    val edit = new ReplaceEdit(change.from, change.to - change.from, change.text)
    val document = editor.getDocumentProvider.getDocument(editor.getEditorInput)
    edit.apply(document)
  }

  /**
   * Enters the editor in the LinkedModeUI with the given list of positions.
   * A position is given as an offset and the length.
   */
  def enterLinkedModeUi(ps: List[(Int, Int)]) {

    EditorHelpers.doWithCurrentEditor { editor =>

      val model = new LinkedModeModel {
        this addGroup new LinkedPositionGroup {
          val document = editor.getDocumentProvider.getDocument(editor.getEditorInput)
          ps foreach (p => addPosition(new LinkedPosition(document, p._1, p._2)))
        }
        forceInstall
      }

      (new LinkedModeUI(model, editor.getViewer)).enter
    }
  }
}