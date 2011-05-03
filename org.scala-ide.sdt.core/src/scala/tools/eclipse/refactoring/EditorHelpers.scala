/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

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
}