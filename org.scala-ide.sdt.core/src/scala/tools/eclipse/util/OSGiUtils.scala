/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import java.net.URL
import org.eclipse.core.runtime.{ FileLocator, IPath, Path }
import org.osgi.framework.Bundle

object OSGiUtils {
  private def urlToPath(url: URL): IPath = Path.fromOSString(FileLocator.toFileURL(url).getPath)

  def pathInBundle(bundle: Bundle, path: String) : Option[IPath] = {
    val url = FileLocator.find(bundle, Path.fromPortableString(path), null)
    Option(url) map urlToPath
  }

  def allPathsInBundle(bundle: Bundle, path: String, filePattern: String): Iterator[IPath] = {
    import scala.collection.JavaConverters._
    bundle.findEntries(path, filePattern, false).asScala map urlToPath
  }
}
