package org.scalaide.util.eclipse

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
import java.io.IOException

object OSGiUtils {

  private def urlToPath(url: URL): IPath = Path.fromOSString(FileLocator.toFileURL(url).getPath)

  /**
   * Uses [[FileLocator.find]] to find a path in a given bundle.
   */
  def pathInBundle(bundle: Bundle, path: String): Option[IPath] = {
    val url = FileLocator.find(bundle, Path.fromPortableString(path), null)
    Option(url) map urlToPath
  }

  /**
   *  Uses [[FileLocator.getBundlePath]] to obtain the path of a bundle.
   *  accepts `null`
   */
  def getBundlePath(bundle: Bundle): Option[IPath] = util.control.Exception.failing(classOf[IOException]) {
    Option(bundle).map(b => Path.fromOSString(FileLocator.getBundleFile(b).getAbsolutePath()))
  }

  /**
   * Read the content of a file whose `filePath` points to a location in a
   * given `bundleId` and returns them. A [[scala.util.Failure]] is returned if
   * either the file could not be found or if it could not be accessed.
   */
  def fileContentFromBundle(bundleId: String, filePath: String): util.Try[String] = util.Try {
    val e = Option(Platform.getBundle(bundleId)).flatMap(b => Option(b.getEntry(filePath)))
    e.fold(throw new FileNotFoundException(s"$bundleId$filePath")) { e =>
      val s = io.Source.fromInputStream(e.openStream())(io.Codec.UTF8)
      val res = s.mkString
      s.close()
      res
    }
  }

  /** Returns the ImageDescriptor for the image at the given {{path}} in
   *  the Scala IDE core bundle.
   *  Returns the default missing image descriptor if the path is invalid.
   */
  def getImageDescriptorFromCoreBundle(path: String): ImageDescriptor =
    getImageDescriptorFromBundle(SdtConstants.PluginId, path)

  /** Returns the ImageDescriptor for the image at the given {{path}} in
   *  the bundle with the given {{id}}.
   *  Returns the default missing image descriptor if the bundle is not
   *  in a running state, or if the path is invalid.
   */
  def getImageDescriptorFromBundle(bundleId: String, path: String): ImageDescriptor =
    Option(AbstractUIPlugin.imageDescriptorFromPlugin(bundleId, path))
      .getOrElse(ScalaImages.MISSING_ICON)

}
