/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import java.io.IOException
import java.net.URL

import scala.tools.eclipse.logging.HasLogger

import org.eclipse.core.runtime.FileLocator
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.osgi.framework.Bundle

object OSGiUtils extends HasLogger {
  private def urlToPath(url: URL): IPath = Path.fromOSString(FileLocator.toFileURL(url).getPath)

  def pathInBundle(bundle: Bundle, path: String): Option[IPath] = {
    val url = FileLocator.find(bundle, Path.fromPortableString(path), null)
    Option(url) map urlToPath
  }

  def getBundlePath(bundle: Bundle): Option[IPath] = util.control.Exception.failing(classOf[IOException]) {
    Option(FileLocator.getBundleFile(bundle)).map(f => Path.fromOSString(f.getAbsolutePath()))
  }

  def allPathsInBundle(bundle: Bundle, path: String, filePattern: String): Iterator[IPath] = {
    import scala.collection.JavaConverters._
    bundle.findEntries(path, filePattern, false).asScala map urlToPath
  }
}
