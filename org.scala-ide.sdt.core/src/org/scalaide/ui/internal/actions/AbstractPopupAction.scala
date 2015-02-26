package org.scalaide.ui.internal.actions

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.ui.IObjectActionDelegate
import org.eclipse.ui.IWorkbenchPart
import org.scalaide.util.eclipse.EclipseUtils.RichAdaptable
import org.scalaide.util.eclipse.EditorUtils

/**
 * This traits contains definitions to handle the invocation of menu entries
 * that are shown in the "Scala" context menu.
 *
 * Extends [[AbstractHandler]] to allow users to call this action by
 * key bindings.
 */
trait AbstractPopupAction extends AbstractHandler with IObjectActionDelegate {
  private var selectionOption: Option[ISelection] = None

  /**
   * This method is called if either the menu entry is invoked or the handler
   * (if it exists) is called when an [[IResource]] element is selected.
   */
  def performAction(project: IProject)

  override def execute(event: ExecutionEvent): AnyRef = {
    EditorUtils.resourceOfActiveEditor flatMap (r ⇒ Option(r.getProject)) foreach performAction
    null
  }

  override def selectionChanged(action: IAction, selection: ISelection) { this.selectionOption = Option(selection) }

  override def run(action: IAction) = {
    for {
      selection <- selectionOption collect { case s: IStructuredSelection => s }
      selObject <- selection.toArray
      project <- selectionObjectToProject(selObject)
    } performAction(project)
  }

  private def selectionObjectToProject(selectionElement: Object): Option[IProject] = selectionElement match {
    case project: IProject => Some(project)
    case adaptable: IAdaptable => adaptable.adaptToOpt[IProject]
    case _ => None
  }

  def setActivePart(action: IAction, targetPart: IWorkbenchPart) {  }
}
