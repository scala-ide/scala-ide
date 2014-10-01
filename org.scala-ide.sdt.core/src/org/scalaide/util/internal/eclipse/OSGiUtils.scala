package org.scalaide.util.internal.eclipse

import java.io.FileNotFoundException
import java.io.IOException
import java.net.URL
import org.eclipse.core.runtime.FileLocator
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.Platform
import org.osgi.framework.Bundle
import org.scalaide.core.SdtConstants
import org.scalaide.ui.ScalaImages
import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.ui.plugin.AbstractUIPlugin

object OSGiUtils extends org.scalaide.util.OSGiUtils {
  private def urlToPath(url: URL): IPath = Path.fromOSString(FileLocator.toFileURL(url).getPath)

  def pathInBundle(bundle: Bundle, path: String): Option[IPath] = {
    val url = FileLocator.find(bundle, Path.fromPortableString(path), null)
    Option(url) map urlToPath
  }

  def getBundlePath(bundle: Bundle): Option[IPath] = util.control.Exception.failing(classOf[IOException]) {
    Option(bundle).map(b => Path.fromOSString(FileLocator.getBundleFile(b).getAbsolutePath()))
  }

  def allPathsInBundle(bundle: Bundle, path: String, filePattern: String): Iterator[IPath] = {
    import scala.collection.JavaConverters._
    bundle.findEntries(path, filePattern, false).asScala map urlToPath
  }

  def fileContentFromBundle(bundleId: String, filePath: String): util.Try[String] = util.Try {
    val e = Option(Platform.getBundle(bundleId)).flatMap(b => Option(b.getEntry(filePath)))
    e.fold(throw new FileNotFoundException(s"$bundleId$filePath")) { e =>
      val s = io.Source.fromInputStream(e.openStream(), "UTF-8")
      val res = s.mkString
      s.close()
      res
    }
  }

  def getImageDescriptorFromCoreBundle(path: String): ImageDescriptor =
    getImageDescriptorFromBundle(SdtConstants.PluginId, path)

  def getImageDescriptorFromBundle(bundleId: String, path: String): ImageDescriptor =
    Option(AbstractUIPlugin.imageDescriptorFromPlugin(bundleId, path))
      .getOrElse(ScalaImages.MISSING_ICON)

}
