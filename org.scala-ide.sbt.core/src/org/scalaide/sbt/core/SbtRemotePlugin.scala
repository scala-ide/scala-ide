package org.scalaide.sbt.core

import java.io.File

import org.eclipse.core.runtime.FileLocator
import org.eclipse.core.runtime.Path
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext

object SbtRemotePlugin {

  @volatile
  var plugin: SbtRemotePlugin = _

}

class SbtRemotePlugin extends AbstractUIPlugin {
  import SbtRemotePlugin._

  lazy val SbtLaunchJarLocation = libOsLocationFromBundle("sbt-launch.jar")
  lazy val SbtRcUiInterface012JarLocation = libOsLocationFromBundle("sbt-rc-ui-interface-0-12.jar")
  lazy val SbtRcProbe012JarLocation = libOsLocationFromBundle("sbt-rc-probe-0-12.jar")
  lazy val SbtRcUiInterface013JarLocation = libOsLocationFromBundle("sbt-rc-ui-interface-0-13.jar")
  lazy val SbtRcProbe013JarLocation = libOsLocationFromBundle("sbt-rc-probe-0-13.jar")
  lazy val SbtRcPropsJarLocation = libOsLocationFromBundle("sbt-rc-props.jar")

  lazy val resources012 = List(SbtRcUiInterface012JarLocation)
  lazy val resources013 = List(SbtRcUiInterface013JarLocation)
  lazy val controlerClasspath012 = List(
    SbtRemotePlugin.plugin.SbtRcProbe012JarLocation,
    SbtRemotePlugin.plugin.SbtRcPropsJarLocation).map(new File(_))
  lazy val controlerClasspath013 = List(
    SbtRemotePlugin.plugin.SbtRcProbe013JarLocation,
    SbtRemotePlugin.plugin.SbtRcPropsJarLocation).map(new File(_))

  override def start(context: BundleContext) {
    super.start(context)
    plugin = this
  }

  private def libOsLocationFromBundle(fileName: String): String =
    FileLocator.toFileURL(FileLocator.find(getBundle, Path.fromPortableString("/target/lib/" + fileName), null)).getPath()

}