/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import org.eclipse.core.runtime.{ FileLocator, IPath, Path }
import org.osgi.framework.Bundle

object OSGiUtils {
  def pathInBundle(bundle: Bundle, path: String) : Option[IPath] = {
    val url = FileLocator.find(bundle, Path.fromPortableString(path), null)
    Option(url) map { x => Path.fromOSString(FileLocator.toFileURL(x).getPath) }
  }
}
