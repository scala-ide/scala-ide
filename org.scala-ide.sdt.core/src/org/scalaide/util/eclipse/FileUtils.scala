package org.scalaide.util.eclipse

import java.io.ByteArrayInputStream
import java.io.File

import scala.tools.nsc.io.AbstractFile
import scala.util.Try

import org.eclipse.core.internal.resources.ResourceException
import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IFolder
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.Path
import org.scalaide.core.resources.EclipseResource
import org.eclipse.core.filesystem.URIUtil
import org.eclipse.core.runtime.IPath
import org.eclipse.core.internal.resources.ResourceException
import java.io.File
import org.scalaide.core.SdtConstants
import scala.util.control.NonFatal
import org.scalaide.core.internal.logging.EclipseLogger
import java.net.URI

object FileUtils {
  import EclipseUtils.workspaceRoot

  /**
   * Tries to obtain the most accurate [[IFile]] embedded in an [[AbstractFile]],
   * whether through subtyping or path-related methods.
   */
  def toIFile(file: AbstractFile): Option[IFile] = file match {
    case null                         => None
    case EclipseResource(file: IFile) => Some(file)
    case abstractFile =>
      val path = Path.fromOSString(abstractFile.path)
      fileResourceForPath(path)
  }

  def toIFolder(path: String, create: Boolean = true): Option[IFolder] = {
    try {
      containerResourceForPath(path).collect { case folder: IFolder =>
        if (create && !folder.exists()) {
          createWithParents(folder)
        }
        folder
      }
    } catch {
      case NonFatal(e) =>
        EclipseLogger.warn("Could not convert to IFolder", e)
        None
    }
  }

  /**
   * Returns the full path of this file.
   */
  def toIPath(file: AbstractFile): Option[IPath] = {
    toIFile(file).map(_.getFullPath)
  }

  /**
   * Removes all problem markers from this IFile.
   */
  def clearBuildErrors(file: IFile, monitor: IProgressMonitor) =
    try {
      file.deleteMarkers(SdtConstants.ProblemMarkerId, true, IResource.DEPTH_INFINITE)
    } catch {
      case _: ResourceException =>
    }

  /**
   * Returns all problem markers for a given file.
   */
  def findBuildErrors(file: IResource): Seq[IMarker] =
    file.findMarkers(SdtConstants.ProblemMarkerId, true, IResource.DEPTH_INFINITE)

  /**
   * Returns true if the file bears problem markers with error severity.
   */
  def hasBuildErrors(file: IResource): Boolean =
    file.findMarkers(SdtConstants.ProblemMarkerId, true, IResource.DEPTH_INFINITE).exists(_.getAttribute(IMarker.SEVERITY) == IMarker.SEVERITY_ERROR)

  /** Delete directory recursively. Does nothing if dir is not a directory. */
  def deleteDir(dir: File): Unit = {
    if (dir.isDirectory()) {
      for (f <- dir.listFiles())
        if (f.isDirectory) deleteDir(f) else f.delete()
      dir.delete()
    }
  }

  private def createWithParents(c: IContainer): Unit = c match {
    case f: IFolder if !f.exists() =>
      createWithParents(f.getParent())
      f.create(/* force */ true, /* local */ true, null)
    case _ =>
      ()
  }

  /** Creates a file of a given `IFile` and all of its parent folders if needed.
   *  Resource listeners are also notified about the changes.
   *
   *  Returns `Unit` if the file creation was successful, otherwise the thrown
   *  exception.
   */
  def createFile(file: IFile): Try[Unit] = Try {
    createWithParents(file.getParent())
    file.create(new ByteArrayInputStream(Array()), /* force */ true, null)
  }

  /**
   * Find a File that matches the given absolute location on the file system. Since a given
   * file might "mounted" under multiple locations in the Eclipse file system, the `prefix`
   * path is used disambiguate.
   */
  def fileResourceForPath(location: IPath, prefix: IPath = Path.EMPTY): Option[IFile] = {
    lookupPathWithPrefix(location, prefix)(workspaceRoot.findFilesForLocationURI)
  }

  private def containerResourceForPath(location: String, prefix: IPath = Path.EMPTY): Option[IContainer] = {
    containerResourceForPath(Path.fromOSString(location), prefix)
  }

  private def containerResourceForPath(location: IPath, prefix: IPath): Option[IContainer] = {
    lookupPathWithPrefix(location, prefix)(workspaceRoot.findContainersForLocationURI)
  }

  private def lookupPathWithPrefix[ResourceT <: IResource](location: IPath, prefix: IPath)(findForURI: URI => Array[ResourceT]): Option[ResourceT] = {
    try {
      val resources = findForURI(URIUtil.toURI(location))
      resources.find(r => prefix.isPrefixOf(r.getFullPath))
    } catch {
      case NonFatal(e) =>
        EclipseLogger.error(s"Error looking up $location for $prefix", e)
        None
    }
  }

  /** Is the file buildable by the Scala plugin? In other words, is it a
   *  Java or Scala source file?
   *
   *  @note If you don't have an IFile yet, prefer the String overload, as
   *        creating an IFile is usually expensive
   */
  def isBuildable(file: IFile): Boolean =
    isBuildable(file.getName())

  /**
   * @see [[isBuildable(IFile):Boolean]
   */
  def isBuildable(fileName: String): Boolean =
    (fileName.endsWith(SdtConstants.ScalaFileExtn) || fileName.endsWith(SdtConstants.JavaFileExtn))
}
