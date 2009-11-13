/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable.ArrayBuffer

import org.eclipse.core.resources.{ IProject, IProjectNature, IResource }
import org.eclipse.jdt.core.{ IClasspathEntry, JavaCore }
import org.eclipse.jdt.launching.JavaRuntime
import org.eclipse.core.runtime.Path

class Nature extends IProjectNature {
  protected def plugin : ScalaPlugin = ScalaPlugin.plugin
  
  private var project : IProject = _

  override def getProject = project
  override def setProject(project : IProject) = this.project = project
  
  override def configure : Unit = {
    if (project == null || !project.isOpen) {
      plugin.logError("", null); return
    }
    
    replaceBuilder(project, JavaCore.BUILDER_ID, plugin.builderId)
    
    plugin.check {
      val jp = JavaCore.create(getProject)
      val buf = new ArrayBuffer[IClasspathEntry]
      buf ++= jp.getRawClasspath
      
      // Put the Scala classpath container before JRE container
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
      ()
    }
  }
  
  //TODO - Do we need to override deconfigure to undo the above operations?
  override def deconfigure : Unit = {

    if (project == null || !project.isOpen) {
      plugin.logError("", null); return
    }
    
    replaceBuilder(project, plugin.builderId, JavaCore.BUILDER_ID)
    
    val jp = JavaCore.create(getProject)
    val scalaLibPath = Path.fromPortableString(plugin.scalaLibId)
    //TODO - Reset JavaCore Properties to default, or as defined *before* scala nature was added.
        
    //Pull scala lib off the path
    val buf = jp.getRawClasspath filter { entry => !entry.getPath.equals(scalaLibPath) }

    jp.setRawClasspath(buf, null)
    jp.save(null, true)
  }
  
  private def replaceBuilder(project: IProject, builderToRemove: String, builderToAdd: String) {
    plugin.check {
      val description = project.getDescription
      val previousCommands = description.getBuildSpec
      val newBuilderCommandIfNecessary = 
        if (previousCommands.exists( _.getBuilderName == builderToAdd )) 
          Array() 
        else {
          val newBuilderCommand = description.newCommand;
          newBuilderCommand.setBuilderName(builderToAdd);
          Array(newBuilderCommand)
        }
      val newCommands = previousCommands.filter( _.getBuilderName != builderToRemove ) ++ newBuilderCommandIfNecessary
      description.setBuildSpec(newCommands)
      project.setDescription(description, IResource.FORCE, null)
    }
  }
  
}
