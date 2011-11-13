/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package actions

import org.eclipse.core.resources.{ IProject }
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.{ ISelection, IStructuredSelection }
import org.eclipse.core.runtime.Platform
import org.eclipse.ui.{ IObjectActionDelegate, IWorkbenchPart }
import ScalaPlugin.plugin
import scala.tools.eclipse.util.Utils

object ToggleScalaNatureAction {
  val PDE_PLUGIN_NATURE = "org.eclipse.pde.PluginNature" /* == org.eclipse.pde.internal.core.natures.PDE.PLUGIN_NATURE */
  val PDE_BUNDLE_NAME = "org.eclipse.pde.ui"
}

class ToggleScalaNatureAction extends AbstractPopupAction {  
  import ToggleScalaNatureAction._
  
  override def performAction(project: IProject) {
    toggleScalaNature(project)
  }
  
  private def toggleScalaNature(project: IProject) =
    Utils tryExecute {
      if (project.hasNature(plugin.natureId) || project.hasNature(plugin.oldNatureId)) {
        doIfPdePresent(project) { ScalaLibraryPluginDependencyUtils.removeScalaLibraryRequirement(project) }
        updateNatureIds(project) { _ filterNot Set(plugin.natureId, plugin.oldNatureId) }
      } else {
        doIfPdePresent(project) { ScalaLibraryPluginDependencyUtils.addScalaLibraryRequirement(project) }
        updateNatureIds(project) { plugin.natureId +: _ }
      }
    }

  private def doIfPdePresent(project: IProject)(proc: => Unit) =
    if (project.hasNature(PDE_PLUGIN_NATURE) && Platform.getBundle(PDE_BUNDLE_NAME) != null)
      proc

  private def updateNatureIds(project: IProject)(natureIdUpdater: Array[String] => Array[String]) {
    val projectDescription = project.getDescription
    val currentNatureIds = projectDescription.getNatureIds
    val updatedNatureIds = natureIdUpdater(currentNatureIds)
    projectDescription.setNatureIds(updatedNatureIds)
    project.setDescription(projectDescription, null)
    project.touch(null)
  }
}
