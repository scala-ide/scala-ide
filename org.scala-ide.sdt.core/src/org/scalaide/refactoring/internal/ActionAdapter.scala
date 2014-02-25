package org.scalaide.refactoring.internal

import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IEditorActionDelegate
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IWorkbenchWindowActionDelegate

/**
 * A simple adapter to unify Eclipse's different action interfaces.
 */
trait ActionAdapter extends Action with IWorkbenchWindowActionDelegate with IEditorActionDelegate {

  def setActiveEditor(action: IAction, targetEditor: IEditorPart) = ()

  def init(window: IWorkbenchWindow) = ()

  def dispose() = ()

  // adapt from Action to the ActionDelegate
  override def run() = run(this)

  def selectionChanged(action: IAction, selection: ISelection) = ()
}
