package org.scalaide.core.internal.jdt.util

import scala.tools.nsc.settings.ScalaVersion
import org.scalaide.core.internal.project.ScalaInstallation
import org.eclipse.core.runtime.NullProgressMonitor
import org.scalaide.core.ScalaPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IClasspathEntry
import org.scalaide.core.internal.jdt.util.ScalaClasspathContainerHandler
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

  def getContainerStateFile(project:IProject) = {
    new File(ScalaPlugin.plugin.getStateLocation().toFile(), project.getName() + ".container")
  }

  def saveContainerState(project: IProject, container: IClasspathContainer): Unit = {
    val containerStateFile = getContainerStateFile(project)
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

  def getSavedContainer(project: IProject): Option[IClasspathContainer] = {
    val containerStateFile = getContainerStateFile(project)
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

  def getAndUpdateScalaClasspathContainerEntry(id: String, desc: String, versionString: String, project: IJavaProject, si:ScalaInstallation, existingEntries: Array[IClasspathEntry]): IClasspathEntry = {
    val containerPath: IPath = new Path(id)

    val customContainer : IClasspathContainer = new IClasspathContainer() {
      override def getClasspathEntries() = classpathEntriesOfScalaInstallation(si)
      override def getDescription(): String = desc + s" [ $versionString ]"
      override def getKind(): Int = IClasspathContainer.K_SYSTEM
      override def getPath(): IPath = containerPath
    }

   if (!hasCustomContainer(existingEntries, containerPath)) {
      logger.debug(s"Did not find a container for $id on classpath when asked to update to $versionString — adding Container")
      JavaCore.setClasspathContainer(containerPath, Array(project),Array(customContainer), null)
   }
   else {
     logger.debug(s"Found container for $id on classpath when asked to update to $versionString — updating existing semantics")
     containerUpdater(containerPath, customContainer)
   }
   saveContainerState(project.getProject(), customContainer)
   if (!hasCustomContainer(existingEntries, containerPath)) JavaCore.newContainerEntry(containerPath) else null
  }
}

class ClasspathContainerSetter(val javaProject: IJavaProject) extends ScalaClasspathContainerHandler {

  override def classpathEntriesOfScalaInstallation(si: ScalaInstallation): Array[IClasspathEntry] = (si.library +: si.extraJars).map(libraryEntries).toArray

  override def containerUpdater(containerPath: IPath, container: IClasspathContainer) = (new ScalaLibraryClasspathContainerInitializer()).requestClasspathContainerUpdate(containerPath, javaProject, container)

  private def bestScalaBundleForVersion(scalaVersion: ScalaVersion) = {
    import org.scalaide.util.internal.CompilerUtils.isBinarySame
    val available = ScalaInstallation.availableInstallations
    available.filter { si => isBinarySame(scalaVersion, si.version) }.sortBy(_.version).lastOption
  }

  def updateLibraryBundleFromSourceLevel(scalaVersion: ScalaVersion) = {
    val entries = javaProject.getRawClasspath()
    val newEntry = bestScalaBundleForVersion(scalaVersion) flatMap { best => Option(getAndUpdateScalaClasspathContainerEntry(ScalaPlugin.plugin.scalaLibId, "Scala Library Container", best.version.unparse, javaProject, best, entries)) }
    newEntry foreach { e => javaProject.setRawClasspath((e +: entries).distinct, new NullProgressMonitor()) }
  }

}
