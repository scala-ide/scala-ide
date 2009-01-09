/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable.ArrayBuffer

import org.eclipse.jdt.core.{ IClasspathEntry, JavaCore }
import org.eclipse.jdt.launching.JavaRuntime
import org.eclipse.core.runtime.Path

class Nature extends lampion.eclipse.Nature {
  protected def plugin : ScalaPlugin = ScalaPlugin.plugin
  override protected def requiredBuilders =
    plugin.builderId :: Nil
  override protected def unrequiredBuilders =
    JavaCore.BUILDER_ID :: Nil

  override def configure = {
    super.configure
    plugin.check {
      val jp = plugin.javaProject(getProject).get
      jp.setOption(JavaCore.CORE_JAVA_BUILD_CLEAN_OUTPUT_FOLDER, "ignore")
      jp.setOption(JavaCore.CORE_JAVA_BUILD_RESOURCE_COPY_FILTER, "*.scala")
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
  override def deconfigure = {
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
