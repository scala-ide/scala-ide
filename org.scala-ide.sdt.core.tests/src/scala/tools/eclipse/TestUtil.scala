package scala.tools.eclipse

import org.osgi.framework.Version

object TestUtil {

  def installedScalaVersionGreaterOrEqualsTo(version: Version): Boolean = {
    ScalaPlugin.plugin.scalaLibBundle.getVersion().compareTo(version) >= 0
  }

}