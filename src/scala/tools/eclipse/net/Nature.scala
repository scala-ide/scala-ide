/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.net

import org.eclipse.jdt.core._
import org.eclipse.jdt.launching._
import org.eclipse.core.runtime._

import scala.collection.mutable.ArrayBuffer

class Nature extends lampion.eclipse.Nature {
  protected def plugin : ScalaPlugin = ScalaPlugin.plugin
  override def configure = {
    super.configure
    plugin.check{
    //val project = plugin.projects(getProject)
    val jp = plugin.javaProject(getProject).get
    val buf = new ArrayBuffer[IClasspathEntry]
    buf ++= jp.getRawClasspath.filter(_.getEntryKind != IClasspathEntry.CPE_CONTAINER).map{e => 
      if (e.getEntryKind == IClasspathEntry.CPE_SOURCE) {
        val src = getProject.getFolder("src")
        if (!src.exists()) src.create(true, true, null);
        JavaCore.newSourceEntry(src.getFullPath)
      } else e
    }
    jp.setRawClasspath(buf.toArray, null)
    jp.save(null, true)
    val plugin0 = plugin.asInstanceOf[ScalaMSILPlugin]
    plugin0.projectSafe(getProject).foreach{project => 
      project.initAssemblies
      project.add(plugin0.ScalaLibAssembly)
    }
  }}

}
