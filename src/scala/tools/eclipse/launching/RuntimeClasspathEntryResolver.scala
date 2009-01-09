/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.launching
import org.eclipse.core.runtime._
import org.eclipse.debug.core._
import org.eclipse.jdt.core._
import org.eclipse.jdt.launching._

class RuntimeClasspathEntryResolver extends IRuntimeClasspathEntryResolver {
  import ContainerInitializer._
  protected def resolveRuntimeClasspathEntry0(entry : IRuntimeClasspathEntry) = {
    val path = (entry.getClasspathEntry.getPath)
    val de = decode(path)
    de._1.map{
    case (classes,_) =>
      assert(classes.lastSegment.endsWith(".jar"))
      JavaRuntime.newArchiveRuntimeClasspathEntry(classes)
    }.toArray
  }
  def resolveRuntimeClasspathEntry(entry : IRuntimeClasspathEntry, launch : ILaunchConfiguration) = 
    resolveRuntimeClasspathEntry0(entry)
  def resolveRuntimeClasspathEntry(entry : IRuntimeClasspathEntry, project : IJavaProject) = 
    resolveRuntimeClasspathEntry0(entry)
  def resolveVMInstall(entry : IClasspathEntry) = null
}
