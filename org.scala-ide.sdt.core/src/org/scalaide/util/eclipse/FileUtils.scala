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
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.Path
import org.scalaide.core.resources.EclipseResource
import org.eclipse.core.filesystem.URIUtil
import org.eclipse.core.runtime.IPath
import org.eclipse.core.internal.resources.ResourceException
import java.io.File
import org.scalaide.core.SdtConstants

object FileUtils {

  /**
   * Tries to obtain the most accurate [[IFile]] embedded in an [[AbstractFile]],
   * whether through subtyping or path-related methods.
   */
  def toIFile(file: AbstractFile): Option[IFile] = file match {
    case null                         => None
    case EclipseResource(file: IFile) => Some(file)
    case abstractFile =>
      val path = Path.fromOSString(abstractFile.path)
      resourceForPath(path)
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

  /** Creates a file of a given `IFile` and all of its parent folders if needed.
   *  Resource listeners are also notified about the changes.
   *
   *  Returns `Unit` if the file creation was successful, otherwise the thrown
   *  exception.
   */
  def createFile(file: IFile): Try[Unit] = Try {
    def createParentFolders(c: IContainer): Unit = c match {
      case f: IFolder if !f.exists() =>
        createParentFolders(f.getParent())
        f.create(/* force */ true, /* local */ true, null)
      case _ =>
    }

    createParentFolders(file.getParent())
    file.create(new ByteArrayInputStream(Array()), /* force */ true, null)
  }

  /**
   * Find a File that matches the given absolute location on the file system. Since a given
   * file might "mounted" under multiple locations in the Eclipse file system, the `prefix`
   * path is used disambiguate.
   */
  def resourceForPath(location: IPath, prefix: IPath = Path.EMPTY): Option[IFile] = {
    val resources = Try(ResourcesPlugin.getWorkspace.getRoot.findFilesForLocationURI(URIUtil.toURI(location))).getOrElse(Array())
    resources.find(prefix isPrefixOf _.getFullPath)
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
