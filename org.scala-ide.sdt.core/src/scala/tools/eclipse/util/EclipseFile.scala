/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package util

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, File, InputStream, OutputStream }

import org.eclipse.core.filebuffers.FileBuffers
import org.eclipse.core.filesystem.URIUtil
import org.eclipse.core.resources.{ IContainer, IFile, IFolder, IResource, ResourcesPlugin }
import org.eclipse.core.runtime.{ IPath, Path }
import org.eclipse.jdt.core.IBuffer
import org.eclipse.core.filesystem.EFS
import org.eclipse.core.internal.resources.Resource
import javaelements.ScalaSourceFile

import scala.tools.nsc.io.AbstractFile

object EclipseResource {
  def apply(r : IResource) : EclipseResource[_ <: IResource] = {
    try {
      if (r == null)
        throw new NullPointerException()
      else if (r.getLocation == null)
        throw new NullPointerException(r.toString)
    }
      
    r match {
      case file : IFile => new EclipseFile(file)
      case container : IContainer => new EclipseContainer(container)
    }
  }

  def unapply(file : AbstractFile) : Option[IResource] = file match {
    case ef : EclipseFile => Some(ef.underlying)
    case ec : EclipseContainer => Some(ec.underlying)
    case _ => None
  }
  
  def fromString(path : String) : Option[EclipseResource[_ <: IResource]] = {
    def resourceForPath(p : IPath) = {
      val resources = ResourcesPlugin.getWorkspace.getRoot.findFilesForLocationURI(URIUtil.toURI(p))
      if (resources != null && resources.length > 0) Some(resources(0)) else None
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
// for equals/hashCode using path is better but need to many computation see ticket #1000193
abstract class EclipseResource[R <: IResource] extends AbstractFile {
  val underlying : R
  
  def name: String = underlying.getName
  
  private def location = {
    if (underlying == null)
      throw new NullPointerException("underlying == null")
    else if (underlying.getLocation == null)
      throw new NullPointerException("underlying.getLocation == null for: "+underlying)
      
    underlying.getLocation
  }

  def path: String = location.toOSString

  def container : AbstractFile = new EclipseContainer(underlying.getParent)
  
  def file: File = location.toFile

  def lastModified: Long = underlying.getLocalTimeStamp
  
  def delete = underlying.delete(true, null)
  
  def create {}
  
  def absolute = this
  
  override def equals(other : Any) : Boolean = other match {
    case otherRes : EclipseResource[r] => underlying == otherRes.underlying
    case _ => false
  }

  override def hashCode() : Int = underlying.hashCode
}

class EclipseFile(override val underlying : IFile) extends EclipseResource[IFile] {
  if (underlying == null)
    throw new NullPointerException("underlying == null")
  
  def isDirectory : Boolean = false

  // try to use a IDocument instead of IBuffer that can require 'openable.getBuffer' => compilation (via request to ScalaStructureBuilder)
  // scan every editors to find if someone currently edit the underling IFile
  // we doen't realy need the Document but a way to read/update content of potential existing editor open on 'underling
  ///TODO find a better way to retreive Editors/IDocument than scanning
  import org.eclipse.jface.text.IDocument
  
  private def document : IDocument = {
    import org.eclipse.ui.PlatformUI
    import org.eclipse.jdt.ui.JavaUI
    import org.eclipse.ui.part.FileEditorInput
    
    var back : IDocument = null
    val dp = JavaUI.getDocumentProvider()
    for (
      window <- PlatformUI.getWorkbench().getWorkbenchWindows();
      page <- window.getPages;
      edr <- page.getEditorReferences
    ) {
      edr.getEditorInput() match {
        case ed : FileEditorInput if (ed.getFile == underlying) =>
          back = dp.getDocument(ed)
        case _ => () //ignore
      }
    }
    back
  }
  
  def input : InputStream = {
    val doc = document  
    if (doc ne null) new ByteArrayInputStream(doc.get.getBytes) else underlying.getContents(true)
  }
  
  def output: OutputStream = {
    val doc = document  
    if (doc ne null) new ByteArrayOutputStream {
      override def close = {
        doc.set(new String(buf, 0, count))
      }
    } else new ByteArrayOutputStream {
      override def close = {
        val contents = new ByteArrayInputStream(buf, 0, count)
        if (!underlying.exists) {
          def createParentFolder(parent : IContainer) {
            if (!parent.exists()) {
              createParentFolder(parent.getParent)
              parent.asInstanceOf[IFolder].create(true, true, null)
            }
          }
          createParentFolder(underlying.getParent)
          underlying.create(contents, true, null)
        }
      }
    }
  }

  // Size in bytes of char ??
  override def sizeOption: Option[Int] = {
    val doc = document
    if (doc ne null) Some(doc.get.getBytes.length) else getFileInfo.map(_.getLength.toInt)
  }
  
  private def getFileInfo = {
    val fs = FileBuffers.getFileStoreAtLocation(underlying.getLocation)
    if (fs == null)
      None
    else
      Some(fs.fetchInfo)
  }
    
  def iterator : Iterator[AbstractFile] = Iterator.empty

  def lookupName(name : String, directory : Boolean) = null
  
  def lookupNameUnchecked(name : String, directory : Boolean) =
    throw new UnsupportedOperationException("Files cannot have children")
  
  override def equals(other : Any) : Boolean =
    other.isInstanceOf[EclipseFile] && super.equals(other)
}

class EclipseContainer(override val underlying : IContainer) extends EclipseResource[IContainer] {
  if (underlying == null)
    throw new NullPointerException("underlying == null");

  def isDirectory = true
  
  def input = throw new UnsupportedOperationException
  
  def output = throw new UnsupportedOperationException
  
  def iterator : Iterator[AbstractFile] = underlying.members.map(EclipseResource(_)).iterator

  def lookupName(name : String, directory : Boolean) = {
    val r = underlying findMember name
    if (r != null && directory == r.isInstanceOf[IContainer])
      EclipseResource(r)
    else
      null
  }

  override def lookupNameUnchecked(name : String, directory : Boolean) = {
    if (directory)
      new EclipseContainer(underlying.getFolder(new Path(name)))
    else
      new EclipseFile(underlying.getFile(new Path(name)))
  }
  
  override def fileNamed(name : String) : AbstractFile = {
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
  
  override def equals(other : Any) : Boolean =
    other.isInstanceOf[EclipseContainer] && super.equals(other)
}
