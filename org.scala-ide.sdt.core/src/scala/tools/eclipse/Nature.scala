/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable.ArrayBuffer
import org.eclipse.core.resources.{ ICommand, IProject, IProjectNature, IResource }
import org.eclipse.jdt.core.{ IClasspathEntry, IJavaProject, JavaCore }
import org.eclipse.jdt.launching.JavaRuntime
import org.eclipse.core.runtime.Path
import ScalaPlugin.plugin 
import scala.tools.eclipse.util.Utils

object Nature {
  
  def removeScalaLib(jp: IJavaProject) {
    val scalaLibPath = Path.fromPortableString(plugin.scalaLibId)
    val oldScalaLibPath = Path.fromPortableString(plugin.oldScalaLibId)
    val buf = jp.getRawClasspath filter { entry => { val path = entry.getPath ; path != scalaLibPath && path != oldScalaLibPath  } }
    jp.setRawClasspath(buf, null)
  }
  
  /**
   * Removes any existing scala library from the classpath, and adds the ScalaPlugin.scalaLibId 
   * library container to the classpath. Saves the project settings of `jp`.
   */
  def addScalaLibAndSave(project: IProject) {
    val jp = JavaCore.create(project)
    Nature.removeScalaLib(jp)
    
    // Put the Scala classpath container before JRE container
    val buf = ArrayBuffer(jp.getRawClasspath : _*)    
    val scalaLibEntry = JavaCore.newContainerEntry(Path.fromPortableString(plugin.scalaLibId))
    val jreIndex = buf.indexWhere(_.getPath.toPortableString.startsWith(JavaRuntime.JRE_CONTAINER))
    if (jreIndex != -1) {
      buf.insert(jreIndex, scalaLibEntry)
    } else {
      buf += scalaLibEntry
      buf += JavaCore.newContainerEntry(Path.fromPortableString(JavaRuntime.JRE_CONTAINER))
    }
    jp.setRawClasspath(buf.toArray, null)
    
    jp.save(null, true)    
  }
}

class Nature extends IProjectNature {
  private var project : IProject = _

  override def getProject = project
  override def setProject(project : IProject) = this.project = project
  
  override def configure() {
    if (project == null || !project.isOpen)
      return
    
    updateBuilders(project, List(JavaCore.BUILDER_ID, plugin.oldBuilderId), plugin.builderId)
    
    Utils tryExecute {
      Nature.addScalaLibAndSave(getProject)
    }
  }
  
  override def deconfigure() {
    if (project == null || !project.isOpen)
      return

    updateBuilders(project, List(plugin.builderId, plugin.oldBuilderId), JavaCore.BUILDER_ID)

    Utils tryExecute {
      val jp = JavaCore.create(getProject)
      Nature.removeScalaLib(jp)
      jp.save(null, true)
    }
  }
  
  private def updateBuilders(project: IProject, buildersToRemove: List[String], builderToAdd: String) {
    Utils tryExecute {
      val description = project.getDescription
      val previousCommands = description.getBuildSpec
      val filteredCommands = previousCommands.filterNot(buildersToRemove contains _.getBuilderName)
      val newCommands = if (filteredCommands.exists(_.getBuilderName == builderToAdd))
        filteredCommands
      else
        filteredCommands :+ { 
          val newBuilderCommand = description.newCommand;
          newBuilderCommand.setBuilderName(builderToAdd);
          newBuilderCommand
        }
      description.setBuildSpec(newCommands)
      project.setDescription(description, IResource.FORCE, null)
    }
  }
}
