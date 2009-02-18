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
  
  private val requiredBuilders = plugin.builderId :: Nil
  private val unrequiredBuilders = JavaCore.BUILDER_ID :: Nil

  private var project : IProject = _

  override def getProject = project
  override def setProject(project : IProject) = this.project = project
  
  override def configure : Unit = {
    if (project == null || !project.isOpen) {
      plugin.logError("", null); return
    }
    plugin.check {
      val desc = project.getDescription
      val spec =
        requiredBuilders.map(b => { val cmd = desc.newCommand ; cmd.setBuilderName(b) ; cmd }) ++
        desc.getBuildSpec.filter(b => !(requiredBuilders contains b.getBuilderName) && !(unrequiredBuilders contains b.getBuilderName))
      desc.setBuildSpec(spec.toArray)
      project.setDescription(desc, IResource.FORCE, null)
    }
    plugin.check {
      val jp = plugin.javaProject(getProject).get
      val buf = new ArrayBuffer[IClasspathEntry]
      // Scala classpath container before JRE container
      buf ++= jp.getRawClasspath.filter(!_.getPath().equals(Path.fromPortableString(JavaRuntime.JRE_CONTAINER)))
      buf += JavaCore.newContainerEntry(Path.fromPortableString(plugin.scalaLibId))
      buf += JavaCore.newContainerEntry(Path.fromPortableString(JavaRuntime.JRE_CONTAINER))
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

    plugin.check {
      val desc = project.getDescription
      val spec = desc.getBuildSpec.filter(b => !(requiredBuilders contains b.getBuilderName) )
      desc.setBuildSpec(spec)
      project.setDescription(desc, IResource.FORCE, null)
    }
    
    plugin.javaProject(getProject) match {
      case Some(project) => {
        val scalaLibPath = Path.fromPortableString(plugin.scalaLibId)
        //TODO - Reset JavaCore Properties to default, or as defined *before* scala nature was added.
        
        //Pull scala lib off the path
        val buf = project.getRawClasspath filter {
          entry => !entry.getPath.equals(scalaLibPath)
        }
        project.setRawClasspath(buf, null)
        project.save(null, true)
      }
      case None => ScalaPlugin.plugin.logError("Cannot deconfigure an empty proejct!", new RuntimeException("Project was None"))
    }
    ()
  }
}
