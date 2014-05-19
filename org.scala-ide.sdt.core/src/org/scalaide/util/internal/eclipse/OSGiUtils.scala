package org.scalaide.util.internal.eclipse

import java.io.IOException
import java.net.URL

import org.eclipse.core.runtime.FileLocator
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.osgi.framework.Bundle

object OSGiUtils {
  private def urlToPath(url: URL): IPath = Path.fromOSString(FileLocator.toFileURL(url).getPath)

  def pathInBundle(bundle: Bundle, path: String): Option[IPath] = {
    val url = FileLocator.find(bundle, Path.fromPortableString(path), null)
    Option(url) map urlToPath
  }

  /** accepts `null`*/
  def getBundlePath(bundle: Bundle): Option[IPath] = util.control.Exception.failing(classOf[IOException]) {
    Option(bundle).map(b => Path.fromOSString(FileLocator.getBundleFile(b).getAbsolutePath()))
  }

  def allPathsInBundle(bundle: Bundle, path: String, filePattern: String): Iterator[IPath] = {
    import scala.collection.JavaConverters._
    bundle.findEntries(path, filePattern, false).asScala map urlToPath
  }
}
