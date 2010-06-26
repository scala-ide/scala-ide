/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.jface.action.IAction
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.IEditorActionDelegate
import org.eclipse.ui.IWorkbenchWindowActionDelegate

trait ActionAdapter extends IWorkbenchWindowActionDelegate with IEditorActionDelegate {

  def setActiveEditor(action: IAction, targetEditor: IEditorPart) = ()
  
  def init(window: IWorkbenchWindow) = ()
  
  def dispose() = ()
  
  def selectionChanged(action: IAction, selection: ISelection) = ()
}
