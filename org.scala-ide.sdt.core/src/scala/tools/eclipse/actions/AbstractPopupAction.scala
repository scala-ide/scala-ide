package scala.tools.eclipse
package actions

import org.eclipse.core.resources.{ IProject }
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.{ ISelection, IStructuredSelection }
import org.eclipse.core.runtime.Platform
import org.eclipse.ui.{ IObjectActionDelegate, IWorkbenchPart }
import ScalaPlugin.plugin
import scala.tools.eclipse.util.EclipseUtils._

trait AbstractPopupAction extends IObjectActionDelegate {
  private var selectionOption: Option[ISelection] = None

  def performAction(project: IProject)

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
    case adaptable: IAdaptable => adaptable.adaptToSafe[IProject]
    case _ => None
  }

  def setActivePart(action: IAction, targetPart: IWorkbenchPart) {  }
}