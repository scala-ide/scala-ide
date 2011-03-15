/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package actions

import org.eclipse.core.resources.{ IProject }
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.{ ISelection, IStructuredSelection }
import org.eclipse.core.runtime.Platform
import org.eclipse.ui.{ IObjectActionDelegate, IWorkbenchPart }
import ScalaPlugin.plugin

object ToggleScalaNatureAction {
  val PDE_PLUGIN_NATURE = "org.eclipse.pde.PluginNature" /* == org.eclipse.pde.internal.core.natures.PDE.PLUGIN_NATURE */
  val PDE_BUNDLE_NAME = "org.eclipse.pde.ui"
}

class ToggleScalaNatureAction extends IObjectActionDelegate {  
  import ToggleScalaNatureAction._
  
  private var selectionOption: Option[ISelection] = None

  def selectionChanged(action: IAction, selection: ISelection) { this.selectionOption = Option(selection) }

  def run(action: IAction) =
    for {
      selection <- selectionOption
      if selection.isInstanceOf[IStructuredSelection]
      selectionElement <- selection.asInstanceOf[IStructuredSelection].toArray
      project <- convertSelectionToProject(selectionElement)
    } toggleScalaNature(project)

  private def convertSelectionToProject(selectionElement: Object): Option[IProject] = selectionElement match {
    case project: IProject => Some(project)
    case adaptable: IAdaptable => Option(adaptable.getAdapter(classOf[IProject]).asInstanceOf[IProject])
    case _ => None
  }

  private def toggleScalaNature(project: IProject) =
    plugin check {
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

  def setActivePart(action: IAction, targetPart: IWorkbenchPart) {}

}
