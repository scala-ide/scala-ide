package scala.tools.eclipse
package actions

import org.eclipse.core.resources.IProject

class AddScalaLibraryAction extends AbstractPopupAction {
  def performAction(project: IProject) {
    Nature.addScalaLibAndSave(project)
  }
}