/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.core.resources.{ IProject }
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.{ ISelection, IStructuredSelection }
import org.eclipse.ui.{ IObjectActionDelegate, IWorkbenchPart }

/**
 * This class will add/remove the Scala Nature to a project. 
 * @author J. Suereth
 */
class ToggleScalaNatureAction extends IObjectActionDelegate {
  private var selection : Option[ISelection] = None
  private val plugin = ScalaPlugin.plugin
  /*
   * (non-Javadoc)
   * 
   * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
   */
  def run(action : IAction) : Unit = {
   
    /** This will convert a IStructuredSelection instance into a Option[IProject]*/
    def convertSelectionToProject(element : Object) : Option[IProject] = {
      if(element.isInstanceOf[IProject]) {
        return Some(element.asInstanceOf[IProject])
      } else if(element.isInstanceOf[IAdaptable]) {
        val tmp = element.asInstanceOf[IAdaptable].getAdapter(classOf[IProject]).asInstanceOf[IProject]
        if(tmp != null) {
          return Some(tmp)
        }
      }
      return None
    }
    
    //Iterate over all selections and toggle scala natures
    selection foreach { item : ISelection =>
      if (selection.get.isInstanceOf[IStructuredSelection]) {
        item.asInstanceOf[IStructuredSelection].toArray foreach { element =>
          //Pull out the project from the selection
          convertSelectionToProject(element) match {
            case Some(project) => toggleNature(project)
            case _ => //Ignore!
          }
        }
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.IAction,
   *      org.eclipse.jface.viewers.ISelection)
   */
  def selectionChanged( action : IAction, selection : ISelection) : Unit = {
    if(selection != null) {
      this.selection = Some(selection)
    } else {
      this.selection = None
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.eclipse.ui.IObjectActionDelegate#setActivePart(org.eclipse.jface.action.IAction,
   *      org.eclipse.ui.IWorkbenchPart)
   */
  def setActivePart(action : IAction , targetPart : IWorkbenchPart ) : Unit =  {
  }

  /**
   * Toggles sample nature on a project
   * 
   * @param project
   *            to have sample nature added or removed
   */
  private def toggleNature(project : IProject ) : Unit = {
    /** Checks for existence of scala nature*/
    def hasNature : Boolean =  project.hasNature(plugin.natureId)
    /** Adds a nature to the project */
    def addNature : Unit = {
      if (project.hasNature(ToggleScalaNatureAction.PLUGIN_NATURE_ID)) {
        try {
          ScalaLibraryPluginDependencyUtils.addScalaLibraryRequirement(project);
        } catch { 
          case e: NoClassDefFoundError => () // PDE is an optional dependency
        }
      }
      val desc = project.getDescription
      val natures = desc.getNatureIds
      val newNatures : Array[String] = new Array[String](natures.length + 1)
      Array.copy(natures, 0, newNatures, 1, natures.length)
      newNatures(0) = plugin.natureId
      desc.setNatureIds(newNatures)
      project.setDescription(desc, null)
      project.touch(null)
    }
    /** Removes a nature from the project */
    def removeNature : Unit = {
      if (project.hasNature(ToggleScalaNatureAction.PLUGIN_NATURE_ID)) {
        try {
          ScalaLibraryPluginDependencyUtils.removeScalaLibraryRequirement(project);
        } catch { 
          case e: NoClassDefFoundError => () // PDE is an optional dependency
        }
      }
      val desc = project.getDescription
      //Remove this nature from the list
      val natures = desc.getNatureIds filter {
        nature => !(nature equals plugin.natureId)
      }
      desc.setNatureIds(natures)
      project.setDescription(desc, null)
      project.touch(null)
    }
    //Now we actually perform the action
    plugin check {
      if(hasNature) {
        removeNature
      } else {
        addNature
      }
    }
  }
}

object ToggleScalaNatureAction {

  val PLUGIN_NATURE_ID = "org.eclipse.pde.PluginNature" /* == org.eclipse.pde.internal.core.natures.PDE.PLUGIN_NATURE */

}
