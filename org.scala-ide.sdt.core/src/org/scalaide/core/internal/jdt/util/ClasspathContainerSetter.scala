package org.scalaide.core.internal.jdt.util

import scala.tools.nsc.settings.ScalaVersion
import org.scalaide.core.internal.project.ScalaInstallation
import org.eclipse.core.runtime.NullProgressMonitor
import org.scalaide.core.ScalaPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IClasspathEntry
import org.scalaide.core.internal.containers.ScalaLibraryClasspathContainerInitializer
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

trait ClasspathContainerSerializer extends HasLogger {
  import org.scalaide.core.internal.jdt.util.ClasspathContainerSaveHelper._

  protected def libraryEntries(lib: ScalaModule): IClasspathEntry = {
    if (lib.sourceJar.isEmpty) logger.debug(s"No source attachements for ${lib.classJar.lastSegment()}")

    JavaCore.newLibraryEntry(lib.classJar, lib.sourceJar.orNull, null)
  }

  private def getContainerStateFile(project:IProject, path: IPath) = {
    new File(ScalaPlugin.plugin.getStateLocation().toFile(), project.getName() + path.toPortableString() + ".container")
  }

  def saveContainerState(project: IProject, container: IClasspathContainer): Unit = {
    val containerStateFile = getContainerStateFile(project, container.getPath())
    val containerStateFilePath = containerStateFile.getPath()
    logger.debug(s"Trying to write classpath container state to $containerStateFilePath")
    var is: FileOutputStream = null
    try {
      is = new FileOutputStream(containerStateFile)
      writeContainer(container, is)
    } catch {
      case ex: IOException =>
        logger.error("Can't save classpath container state for " + project.getName(), ex)
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch {
          case ex: IOException => logger.error("Can't close output stream for " + containerStateFile.getAbsolutePath(), ex)
        } finally logger.debug(s"Successfully wrote classpath container state to $containerStateFilePath")
      }

    }
  }

  def getSavedContainerForPath(project: IProject, path:IPath): Option[IClasspathContainer] = {
    val containerStateFile = getContainerStateFile(project, path)
    val containerStateFilePath = containerStateFile.getPath()
    logger.debug(s"Trying to read classpath container state from $containerStateFilePath")
    if (!containerStateFile.exists()) None
    else {
      var is: FileInputStream = null
      try {
        is = new FileInputStream(containerStateFile)
        Some(readContainer(is))
      } catch {
        case ex: IOException =>
          throw new CoreException(new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, -1,
            "Can't read classpath container state for " + project.getName(), ex))
        case ex: ClassNotFoundException =>
          throw new CoreException(new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, -1,
            "Can't read classpath container state for " + project.getName(), ex))
      } finally {
        if (is != null) {
          try {
            is.close();
          } catch {
            case ex: IOException => logger.error("Can't close output stream for " + containerStateFile.getAbsolutePath(), ex)
          } finally logger.debug(s"Successfully read classpath container state from $containerStateFilePath")
        }
      }
    }
  }

}

trait ScalaClasspathContainerHandler extends ClasspathContainerSerializer with HasLogger {

  def classpathEntriesOfScalaInstallation(si: ScalaInstallation): Array[IClasspathEntry]

  def containerUpdater(containerPath: IPath, container: IClasspathContainer)

  private def hasCustomContainer(existingEntries: Array[IClasspathEntry], cp: IPath): Boolean = {
   existingEntries.exists(e => e.getEntryKind() == IClasspathContainer.K_SYSTEM && e.getPath().equals(cp))
  }

  def updateScalaClasspathContainerEntry(containerPath: IPath, desc:String, versionString: String, project: IJavaProject, si:ScalaInstallation, existingEntries: Array[IClasspathEntry]): Unit = {
    getAndUpdateScalaClasspathContainerEntry(containerPath, desc, versionString, project, si, existingEntries)
  }

  def getAndUpdateScalaClasspathContainerEntry(containerPath: IPath, desc: String, versionString: String, project: IJavaProject, si:ScalaInstallation, existingEntries: Array[IClasspathEntry]): IClasspathEntry = {

    val customContainer : IClasspathContainer = new IClasspathContainer() {
      override def getClasspathEntries() = classpathEntriesOfScalaInstallation(si)
      override def getDescription(): String = desc + s" [ $versionString ]"
      override def getKind(): Int = IClasspathContainer.K_SYSTEM
      override def getPath(): IPath = containerPath
    }

   if (!hasCustomContainer(existingEntries, containerPath)) {
      logger.debug(s"Did not find a container for ${containerPath.toPortableString()} on classpath when asked to update to $versionString — adding Container")
      JavaCore.setClasspathContainer(containerPath, Array(project),Array(customContainer), null)
   }
   else {
     logger.debug(s"Found container for ${containerPath.toPortableString()} on classpath when asked to update to $versionString — updating existing semantics")
     containerUpdater(containerPath, customContainer)
   }
   saveContainerState(project.getProject(), customContainer)
   if (!hasCustomContainer(existingEntries, containerPath)) JavaCore.newContainerEntry(containerPath) else null
  }
}

class ClasspathContainerSetter(val javaProject: IJavaProject) extends ScalaClasspathContainerHandler {

  override def classpathEntriesOfScalaInstallation(si: ScalaInstallation): Array[IClasspathEntry] = (si.library +: si.extraJars).map(libraryEntries).toArray

  override def containerUpdater(containerPath: IPath, container: IClasspathContainer) = (new ScalaLibraryClasspathContainerInitializer()).requestClasspathContainerUpdate(containerPath, javaProject, container)

  def descOfScalaPath(path: IPath) =
    if (path.toPortableString() == ScalaPlugin.plugin.scalaLibId) "Scala Library container"
    else if (path.toPortableString() == ScalaPlugin.plugin.scalaCompilerId) "Scala Compiler container"
    else "Scala Container"

  def bestScalaBundleForVersion(scalaVersion: ScalaVersion): Option[ScalaInstallation] = {
    import org.scalaide.util.internal.CompilerUtils.isBinarySame
    val available = ScalaInstallation.availableInstallations
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
