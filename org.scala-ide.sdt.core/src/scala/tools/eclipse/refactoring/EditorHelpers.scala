/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import org.eclipse.ui.PlatformUI
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.ui.IFileEditorInput
import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.jface.text.ITextSelection
import scala.tools.eclipse.javaelements.ScalaSourceFile

object EditorHelpers {
   
  private def activeWorkbenchWindow = Option(PlatformUI.getWorkbench.getActiveWorkbenchWindow)
  private def activePage(w: IWorkbenchWindow) = Option(w.getActivePage)
  private def activeEditor(p: IWorkbenchPage) = if(p.isEditorAreaVisible) Some(p.getActiveEditor) else None
  private def textEditor(e: IEditorPart) = e match {case t: ScalaSourceFileEditor => Some(t) case _ => None}
  def file(e: ITextEditor) = e.getEditorInput match {case f: IFileEditorInput => Some(f.getFile) case _ => None}
  def selection(e: ITextEditor) = e.getSelectionProvider.getSelection match {case s: ITextSelection => Some(s) case _ => None}
  
  def withCurrentEditor[T](block: ScalaSourceFileEditor => Option[T]): Option[T] = { 
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
}