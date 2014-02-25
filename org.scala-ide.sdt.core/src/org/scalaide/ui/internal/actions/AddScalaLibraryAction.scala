package org.scalaide.ui.internal.actions

import org.eclipse.core.resources.IProject
import org.scalaide.core.internal.project.Nature

class AddScalaLibraryAction extends AbstractPopupAction {
  def performAction(project: IProject) {
    Nature.addScalaLibAndSave(project)
  }
}
