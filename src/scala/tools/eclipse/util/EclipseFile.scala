/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, File, InputStream, OutputStream }

import org.eclipse.core.filebuffers.FileBuffers
import org.eclipse.core.resources.{ IContainer, IFile, IResource }

import scala.tools.nsc.io.AbstractFile

object EclipseResource {
  def apply(r : IResource) : EclipseResource[_] = r match {
    case file : IFile => new EclipseFile(file);
    case container : IContainer => new EclipseContainer(container)
  }
}

abstract class EclipseResource[R <: IResource] extends AbstractFile {
  val underlying : R
  
  def name: String = underlying.getName

  def path: String = underlying.getLocation.toOSString

  def container : AbstractFile = new EclipseContainer(underlying.getParent)
  
  def file: File = underlying.getLocation.toFile

  def lastModified: Long = underlying.getLocalTimeStamp
}

class EclipseFile(override val underlying : IFile) extends EclipseResource[IFile] {
  def isDirectory : Boolean = false

  def input : InputStream = underlying.getContents
  
  def output: OutputStream = new ByteArrayOutputStream {
    override def close = {
      underlying.setContents(new ByteArrayInputStream(buf, 0, count), true, false, null)
    }
  }

  override def sizeOption: Option[Int] = getFileInfo.map(_.getLength.toInt)
    
  def elements : Iterator[AbstractFile] = Iterator.empty

  def lookupName(name : String, directory : Boolean) = null
  
  private def getFileInfo = {
    val fs = FileBuffers.getFileStoreAtLocation(underlying.getLocation)
    if (fs == null)
      None
    else
      Some(fs.fetchInfo)
  }

}

class EclipseContainer(override val underlying : IContainer) extends EclipseResource[IContainer] {
  def isDirectory = true
  
  def input = throw new UnsupportedOperationException
  
  def output = throw new UnsupportedOperationException
  
  def elements : Iterator[AbstractFile] = underlying.members.map(EclipseResource.apply).elements

  def lookupName(name : String, directory : Boolean) = {
    val r = underlying.findMember(name)
    if (r != null && directory == r.isInstanceOf[IContainer])
      EclipseResource(r)
    else
      null
  }
}
