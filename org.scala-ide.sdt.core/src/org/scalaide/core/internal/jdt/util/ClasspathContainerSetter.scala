package org.scalaide.core.internal.jdt.util

import scala.tools.nsc.settings.ScalaVersion
import org.scalaide.core.internal.project.ScalaInstallation
import org.eclipse.core.runtime.NullProgressMonitor
import org.scalaide.core.ScalaPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.core.runtime.IStatus
import java.io.FileInputStream
import org.scalaide.core.internal.project.ScalaModule
import org.scalaide.logging.HasLogger
import java.io.FileOutputStream
import org.eclipse.core.resources.IProject
import java.io.IOException
import org.eclipse.core.runtime.CoreException
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.runtime.Path
import java.io.File
import org.eclipse.core.runtime.Status

trait ScalaClasspathContainerHandler extends HasLogger {

  protected def hasCustomContainer(existingEntries: Array[IClasspathEntry], cp: IPath, context: Int = IClasspathContainer.K_SYSTEM): Boolean = {
   existingEntries.exists(e => (e.getEntryKind() == context && e.getPath().equals(cp)))
  }

  def updateScalaClasspathContainerEntry(containerPath: IPath, desc:String, versionString: String, project: IJavaProject, si:ScalaInstallation, existingEntries: Array[IClasspathEntry]): Unit = {
    getAndUpdateScalaClasspathContainerEntry(containerPath, desc, versionString, project, si, existingEntries)
  }

  def getAndUpdateScalaClasspathContainerEntry(containerPath: IPath, desc: String, versionString: String, project: IJavaProject, si:ScalaInstallation, existingEntries: Array[IClasspathEntry]): IClasspathEntry = {
    val classpathEntriesOfScalaInstallation : Array[IClasspathEntry]=
      if (containerPath.toPortableString() startsWith(ScalaPlugin.plugin.scalaLibId))
        (si.library +: si.extraJars).map(_.libraryEntries()).toArray
      else if (containerPath.toPortableString() startsWith(ScalaPlugin.plugin.scalaCompilerId))
        Array((si.compiler).libraryEntries())
      else Array()

    val customContainer : IClasspathContainer = new IClasspathContainer() {
      override def getClasspathEntries() = classpathEntriesOfScalaInstallation
      override def getDescription(): String = desc + s" [ $versionString ]"
      override def getKind(): Int = IClasspathContainer.K_SYSTEM
      override def getPath(): IPath = containerPath
    }

   JavaCore.setClasspathContainer(containerPath, Array(project),Array(customContainer), null)
   if (!hasCustomContainer(existingEntries, containerPath)) JavaCore.newContainerEntry(containerPath) else null
  }
}

class ClasspathContainerSetter(val javaProject: IJavaProject) extends ScalaClasspathContainerHandler {

  def descOfScalaPath(path: IPath) =
    if (path.toPortableString() == ScalaPlugin.plugin.scalaLibId) "Scala Library container"
    else if (path.toPortableString() == ScalaPlugin.plugin.scalaCompilerId) "Scala Compiler container"
    else "Scala Container"

  def bestScalaBundleForVersion(scalaVersion: ScalaVersion): Option[ScalaInstallation] = {
    import org.scalaide.util.internal.CompilerUtils.isBinarySame
    val available = ScalaInstallation.availableBundledInstallations
    available.filter { si => isBinarySame(scalaVersion, si.version) }.sortBy(_.version).lastOption
  }

  def updateBundleFromSourceLevel(containerPath: IPath, scalaVersion: ScalaVersion) = {
    bestScalaBundleForVersion(scalaVersion) foreach { best => updateBundleFromScalaInstallation(containerPath, best)}
  }

  def updateBundleFromScalaInstallation(containerPath: IPath, si: ScalaInstallation) = {
    val entries = javaProject.getRawClasspath()
    updateScalaClasspathContainerEntry(containerPath, descOfScalaPath(containerPath), si.version.unparse, javaProject, si, entries)
  }

}
