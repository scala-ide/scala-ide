package scala.tools.eclipse
package actions

import org.eclipse.core.resources.IProject
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.jface.action.IAction

class AddScalaLibraryAction extends AbstractPopupAction {
  def performAction(project: IProject) {
    Nature.addScalaLibAndSave(project)
  }
}