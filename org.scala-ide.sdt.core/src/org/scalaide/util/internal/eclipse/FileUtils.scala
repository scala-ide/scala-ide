package org.scalaide.util.internal.eclipse

import java.io.File

import scala.tools.nsc.io.AbstractFile
import scala.util.Try

import org.eclipse.core.filebuffers.FileBuffers
import org.eclipse.core.internal.resources.ResourceException
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.core.builder.JavaBuilder
import org.scalaide.core.ScalaPlugin.plugin
import org.scalaide.core.resources.EclipseResource

object FileUtils {

  def toIFile(file: AbstractFile): Option[IFile] = file match {
    case null => None
    case EclipseResource(file: IFile) => Some(file)
    case abstractFile =>
      val path = Path.fromOSString(abstractFile.path)
      toIFile(path)
  }

  def toIFile(path: IPath): Option[IFile] = {
    val file = ResourcesPlugin.getWorkspace.getRoot.getFileForLocation(path)

    if (file == null || !file.exists) None
    else Some(file)
  }


  def length(file : IFile) = {
    val fs = FileBuffers.getFileStoreAtLocation(file.getLocation)
    if (fs != null)
      fs.fetchInfo.getLength.toInt
    else
      -1
  }

  def clearBuildErrors(file : IFile, monitor : IProgressMonitor) =
    try {
      file.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    } catch {
      case _ : ResourceException =>
    }

  def clearTasks(file : IFile, monitor : IProgressMonitor) =
    try {
      file.deleteMarkers(plugin.taskMarkerId, true, IResource.DEPTH_INFINITE)
    } catch {
      case _ : ResourceException =>
    }

  def findBuildErrors(file : IResource) : Seq[IMarker] =
    file.findMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)

  def hasBuildErrors(file : IResource) : Boolean =
    file.findMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE).exists(_.getAttribute(IMarker.SEVERITY) == IMarker.SEVERITY_ERROR)

  def task(file: IFile, tag: String, msg: String, priority: String, offset: Int, length: Int, line: Int, monitor: IProgressMonitor) = {
    val mrk = file.createMarker(plugin.taskMarkerId)
    val values = new Array[AnyRef](taskMarkerAttributeNames.length)

    val prioNum = priority match {
      case JavaCore.COMPILER_TASK_PRIORITY_HIGH => IMarker.PRIORITY_HIGH
      case JavaCore.COMPILER_TASK_PRIORITY_LOW  => IMarker.PRIORITY_LOW
      case _                                    => IMarker.PRIORITY_NORMAL
    }

    values(0) = tag + " " + msg
    values(1) = Integer.valueOf(prioNum)
    values(2) = Integer.valueOf(IProblem.Task)
    values(3) = Integer.valueOf(offset)
    values(4) = Integer.valueOf(offset + length + 1)
    values(5) = Integer.valueOf(line)
    values(6) = java.lang.Boolean.valueOf(false)
    values(7) = JavaBuilder.SOURCE_ID
    mrk.setAttributes(taskMarkerAttributeNames, values);
  }

  private val taskMarkerAttributeNames = Array(
    IMarker.MESSAGE,
    IMarker.PRIORITY,
    IJavaModelMarker.ID,
    IMarker.CHAR_START,
    IMarker.CHAR_END,
    IMarker.LINE_NUMBER,
    IMarker.USER_EDITABLE,
    IMarker.SOURCE_ID
  )

  /** Delete directory recursively. Does nothing if dir is not a directory. */
  def deleteDir(dir: File): Unit = {
    if (dir.isDirectory()) {
      for (f <- dir.listFiles())
        if (f.isDirectory) deleteDir(f) else f.delete()
      dir.delete()
    }
  }

  /**
   * Checks if `path` points to a file that exists. `path` needs to be relative
   * to the workspace location, for example `/Project/src/file.scala`.
   */
  def existsWorkspaceFile(path: IPath): Boolean = {
    val root = ResourcesPlugin.getWorkspace().getRoot()
    val fullPath = root.getRawLocation().append(path)
    fullPath.toFile().exists()
  }

  /**
   * Returns the path relative to the workspace if `path` points to a location
   * inside of the workspace. Returns `None` otherwise.
   */
  def workspacePath(path: IPath): Option[IPath] = {
    val root = ResourcesPlugin.getWorkspace().getRoot()
    val rootLocation = root.getRawLocation()

    if (rootLocation.isPrefixOf(path))
      Some(path.removeFirstSegments(rootLocation.segmentCount()))
    else
      None
  }

  /**
   * Creates a file of a given path. The path is expected to be absolute to the
   * root of the filesystem. If `path` points to a location inside of the
   * workspace the project that contains it is automatically refreshed.
   *
   * Returns `Unit` if the file creation was successful, otherwise the thrown
   * exception.
   */
  def createFileFromPath(path: IPath): Try[Unit] = {
    require(path.isAbsolute(), "A path absolute to the root of the filesystem is required")
    Try {
      val f = path.toFile()
      f.getParentFile().mkdirs()
      f.createNewFile()

      workspacePath(path) foreach { p =>
        val root = ResourcesPlugin.getWorkspace().getRoot()
        val project = root.getProject(p.segment(0))
        project.refreshLocal(IResource.DEPTH_INFINITE, null)
      }
    }
  }
}
