/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse;
import org.eclipse.jdt.core._
import org.eclipse.jdt.launching._
import org.eclipse.core.runtime._

import scala.collection.mutable.ArrayBuffer

class Nature extends lampion.eclipse.Nature {
  protected def plugin : ScalaPlugin = ScalaPlugin.plugin
  override protected def requiredBuilders =
    plugin.builderId :: Nil
  override protected def unrequiredBuilders = Nil

  override def configure = {
    super.configure
    plugin.check{
    //val project = plugin.projects(getProject)
    val jp = plugin.javaProject(getProject).get
    jp.setOption(JavaCore.CORE_JAVA_BUILD_CLEAN_OUTPUT_FOLDER, "ignore")
    jp.setOption(JavaCore.CORE_JAVA_BUILD_RESOURCE_COPY_FILTER, "*.scala")
    val buf = new ArrayBuffer[IClasspathEntry]
    buf ++= jp.getRawClasspath.map{e => 
      if (e.getEntryKind == IClasspathEntry.CPE_SOURCE) {
        val src = getProject.getFolder("src")
        if (!src.exists()) src.create(true, true, null);
        JavaCore.newSourceEntry(src.getFullPath)
      } else e
    }
    buf += JavaCore.newContainerEntry(Path.fromPortableString(plugin.scalaLibId));
    buf += JavaCore.newContainerEntry(Path.fromPortableString(JavaRuntime.JRE_CONTAINER))
    jp.setRawClasspath(buf.toArray, null)
    jp.save(null, true)
    ()
  }}
}
