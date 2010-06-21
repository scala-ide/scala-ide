/*
 * Copyright 2005-2010 LAMP/EPFL
 */

// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.core.{ ClasspathContainerInitializer, IClasspathContainer, IJavaProject, JavaCore }
import org.eclipse.core.runtime.IPath

class ScalaClasspathContainerInitializer extends ClasspathContainerInitializer {
  val plugin = ScalaPlugin.plugin
  import plugin._
  
  def initialize(containerPath : IPath, project : IJavaProject) = check {
    val entries = Array(
      JavaCore.newLibraryEntry(libClasses.get, libSources.getOrElse(null), null),
      JavaCore.newLibraryEntry(dbcClasses.get, dbcSources.getOrElse(null), null),
      JavaCore.newLibraryEntry(swingClasses.get, swingSources.getOrElse(null), null)
    )
    
    JavaCore.setClasspathContainer(containerPath, Array(project), Array(new IClasspathContainer {
      def getPath = containerPath
      def getClasspathEntries = entries
      def getDescription = "Scala Library " + scala.util.Properties.versionString
      def getKind = IClasspathContainer.K_DEFAULT_SYSTEM
    }), null)
  }
}
