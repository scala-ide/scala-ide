package org.scalaide.ui.internal.actions

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.Platform
import org.scalaide.core.internal.project.ScalaLibraryPluginDependencyUtils
import org.scalaide.core.SdtConstants
import org.scalaide.util.eclipse.EclipseUtils

object ToggleScalaNatureAction {
  val PDE_PLUGIN_NATURE = "org.eclipse.pde.PluginNature" /* == org.eclipse.pde.internal.core.natures.PDE.PLUGIN_NATURE */
  val PDE_BUNDLE_NAME = "org.eclipse.pde.ui"
}

class ToggleScalaNatureAction extends AbstractPopupAction {
  import ToggleScalaNatureAction._

  override def performAction(project: IProject): Unit = {
    toggleScalaNature(project)
  }

  private def toggleScalaNature(project: IProject): Unit =
    EclipseUtils.withSafeRunner("Couldn't toggle Scala nature") {
      if (project.hasNature(SdtConstants.NatureId)) {
        doIfPdePresent(project) { ScalaLibraryPluginDependencyUtils.removeScalaLibraryRequirement(project) }
        updateNatureIds(project) { _ filterNot (_ == SdtConstants.NatureId) }
      } else {
        doIfPdePresent(project) { ScalaLibraryPluginDependencyUtils.addScalaLibraryRequirement(project) }
        updateNatureIds(project) { SdtConstants.NatureId +: _ }
      }
    }

  private def doIfPdePresent(project: IProject)(proc: => Unit) =
    if (project.hasNature(PDE_PLUGIN_NATURE) && Platform.getBundle(PDE_BUNDLE_NAME) != null)
      proc

  private def updateNatureIds(project: IProject)(natureIdUpdater: Array[String] => Array[String]): Unit = {
    val projectDescription = project.getDescription
    val currentNatureIds = projectDescription.getNatureIds
    val updatedNatureIds = natureIdUpdater(currentNatureIds)
    projectDescription.setNatureIds(updatedNatureIds)
    project.setDescription(projectDescription, null)
    project.touch(null)
  }
}
