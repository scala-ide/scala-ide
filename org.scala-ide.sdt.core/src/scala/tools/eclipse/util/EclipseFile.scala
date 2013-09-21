package scala.tools.eclipse
package util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import org.eclipse.core.filebuffers.FileBuffers
import org.eclipse.core.filesystem.URIUtil
import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IFolder
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import scala.tools.nsc.io.AbstractFile
import scala.tools.eclipse.logging.HasLogger

object EclipseResource extends HasLogger {
  def apply(r: IResource): EclipseResource[_ <: IResource] = r match {
    case file: IFile                  => new EclipseFile(file)
    case container: IContainer        => new EclipseContainer(container)
    case null                         => throw new NullPointerException()
    case r if r.getLocation() == null => throw new NullPointerException(r.toString)
  }

  def unapply(file: AbstractFile): Option[IResource] = file match {
    case ef: EclipseFile      => Some(ef.underlying)
    case ec: EclipseContainer => Some(ec.underlying)
    case _                    => None
  }

  /** Return an `AbstractFile` implementation over Eclipse resources, corresponding to the given
   *   file-system path.
   *
   *   @note A file-system path may correspond to several paths in the workspace. This can happen if
   *         projects are nested: the top-level project sees the file as /toplevel/submodule/file,
   *         and if the submodule is a project itself, the same file will appear as
   *         /submodule/file. See ticket #1000734.
   *
   *   @param path The file-system path for which we need a resource handle
   *
   *   @param prefix An optional workspace-relative path that will be used to filter the possible answers.
   *                 It is usually the name of a project in the workspace, to limit results to resources
   *                 under that project.
   */
  def fromString(path: String, prefix: IPath = Path.EMPTY): Option[EclipseResource[IResource]] = {
    def resourceForPath(p: IPath) = {
      val resources = ResourcesPlugin.getWorkspace.getRoot.findFilesForLocationURI(URIUtil.toURI(p))

      resources match {
        case Array(_, _*) => resources.find(prefix isPrefixOf _.getFullPath)
        case _            => None
      }
    }

    val path0 = new Path(path)
    resourceForPath(path0) match {
      case Some(res) => Some(EclipseResource(res))
      case None =>
        // Attempt to refresh the parent folder and try again
        resourceForPath(path0.removeLastSegments(1)) match {
          case Some(res) =>
            res.refreshLocal(IResource.DEPTH_ONE, null)
            resourceForPath(path0).map(EclipseResource(_))
          case _ => None
        }
    }
  }
}

abstract class EclipseResource[+R <: IResource] extends AbstractFile {
  val underlying: R

  if (underlying eq null)
    throw new NullPointerException("underlying == null")

  def name: String = underlying.getName

  def path: String = {
    var loc = underlying.getLocation
    if (loc eq null)
      throw new NullPointerException("underlying.getLocation == null for: " + underlying)

    loc.toOSString
  }

  def workspacePath: String = underlying.getFullPath.toString

  def container: AbstractFile = new EclipseContainer(underlying.getParent)

  def file: File = underlying.getLocation.toFile

  def lastModified: Long = underlying.getLocalTimeStamp

  def delete: Unit = underlying.delete(true, null)

  def create {}

  def absolute = this

  override def equals(other: Any): Boolean = other match {
    case otherRes: EclipseResource[r] => workspacePath == otherRes.workspacePath
    case _                            => false
  }

  override def hashCode(): Int = workspacePath.hashCode
}

class EclipseFile(override val underlying: IFile) extends EclipseResource[IFile] {
  def isDirectory: Boolean = false

  def input: InputStream = underlying.getContents(true)

  def output: OutputStream = {
    new ByteArrayOutputStream {
      override def close = {
        val contents = new ByteArrayInputStream(buf, 0, count)
        if (!underlying.exists) {
          def createParentFolder(parent: IContainer) {
            if (!parent.exists()) {
              createParentFolder(parent.getParent)
              parent.asInstanceOf[IFolder].create(true, true, null)
            }
          }
          createParentFolder(underlying.getParent)
          underlying.create(contents, true, null)
        } else
          underlying.setContents(contents, true, false, null)
      }
    }
  }

  override def sizeOption: Option[Int] = getFileInfo.map(_.getLength.toInt)

  private def getFileInfo = {
    val fs = FileBuffers.getFileStoreAtLocation(underlying.getLocation)
    if (fs == null)
      None
    else
      Some(fs.fetchInfo)
  }

  def iterator: Iterator[AbstractFile] = Iterator.empty

  def lookupName(name: String, directory: Boolean) = null

  def lookupNameUnchecked(name: String, directory: Boolean) =
    throw new UnsupportedOperationException("Files cannot have children")

  override def equals(other: Any): Boolean =
    other.isInstanceOf[EclipseFile] && super.equals(other)
}

object EclipseFile {
  def unapply(file: AbstractFile): Option[IFile] = file match {
    case ef: EclipseFile => Some(ef.underlying)
    case _               => None
  }
}

class EclipseContainer(override val underlying: IContainer) extends EclipseResource[IContainer] {
  def isDirectory = true

  def input = throw new UnsupportedOperationException

  def output = throw new UnsupportedOperationException

  def iterator: Iterator[AbstractFile] = underlying.members.map(EclipseResource(_)).iterator

  def lookupName(name: String, directory: Boolean) = {
    val r = underlying findMember name
    if (r != null && directory == r.isInstanceOf[IContainer])
      EclipseResource(r)
    else
      null
  }

  override def lookupNameUnchecked(name: String, directory: Boolean) = {
    if (directory)
      new EclipseContainer(underlying.getFolder(new Path(name)))
    else
      new EclipseFile(underlying.getFile(new Path(name)))
  }

  override def fileNamed(name: String): AbstractFile = {
    val existing = lookupName(name, false)
    if (existing == null)
      new EclipseFile(underlying.getFile(new Path(name)))
    else
      existing
  }

  override def subdirectoryNamed(name: String): AbstractFile = {
    val existing = lookupName(name, true)
    if (existing == null)
      new EclipseContainer(underlying.getFolder(new Path(name)))
    else
      existing
  }

  override def equals(other: Any): Boolean =
    other.isInstanceOf[EclipseContainer] && super.equals(other)
}
