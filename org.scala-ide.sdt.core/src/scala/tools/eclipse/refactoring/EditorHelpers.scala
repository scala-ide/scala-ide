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

object EditorHelpers {
   
  def activeWorkbenchWindow: Option[IWorkbenchWindow] = Option(PlatformUI.getWorkbench.getActiveWorkbenchWindow)
  def activePage(w: IWorkbenchWindow): Option[IWorkbenchPage] = Option(w.getActivePage)
  def activeEditor(p: IWorkbenchPage): Option[IEditorPart] = if(p.isEditorAreaVisible) Some(p.getActiveEditor) else None
  def textEditor(e: IEditorPart): Option[ScalaSourceFileEditor] = e match {case t: ScalaSourceFileEditor => Some(t) case _ => None}
  def file(e: ITextEditor): Option[IFile] = e.getEditorInput match {case f: IFileEditorInput => Some(f.getFile) case _ => None}
  def selection(e: ITextEditor): Option[ITextSelection] = e.getSelectionProvider.getSelection match {case s: ITextSelection => Some(s) case _ => None}
  def currentEditor : Option[ScalaSourceFileEditor] = {
    for {
      aww <- activeWorkbenchWindow
      ap <- activePage(aww)
      ae <- activeEditor(ap)
      te <- textEditor(ae)
    } yield te
  }
  
  def withCurrentScalaSourceFile[T](block: ScalaSourceFile => T): Option[T] = {
    for {
      textEditor <- currentEditor
      f <- file(textEditor)
      scalaFile <- ScalaSourceFile.createFromPath(f)
    } yield block(scalaFile)
  }
  
  def withScalaFileAndSelection[T](block: (ScalaSourceFile, ITextSelection) => Option[T]): Option[T] = {
    for {
      textEditor <- currentEditor
      f <- file(textEditor)
      scalaFile <- ScalaSourceFile.createFromPath(f)
      selection <- selection(textEditor)
      back <- block(scalaFile, selection) 
    } yield back
  }
  
  def createTextFileChange(file: IFile, fileChanges: List[scala.tools.refactoring.common.Change]): TextFileChange = {
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
  def applyChangesToFileWhileKeepingSelection(document: IDocument, textSelection: ITextSelection, file: AbstractFile, changes: List[Change])  { 
    
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
          
          for (editor <- currentEditor) {
            editor.selectAndReveal(selectionStart, selectionLength)
            None
          }
      }
    }
  }
}