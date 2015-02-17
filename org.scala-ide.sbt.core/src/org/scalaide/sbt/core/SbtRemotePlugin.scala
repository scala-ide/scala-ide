package org.scalaide.sbt.core

import java.io.File
import org.eclipse.core.runtime.FileLocator
import org.eclipse.core.runtime.Path
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext
import org.eclipse.core.runtime.IPath
import java.net.URL
import java.util.zip.ZipFile
import java.io.FileOutputStream
import akka.actor.ActorSystem
import akka.osgi
import org.osgi.framework.Bundle
import org.osgi.framework.BundleListener
import org.osgi.framework.BundleEvent

object SbtRemotePlugin {

  @volatile var plugin: SbtRemotePlugin = _

  @volatile var system: ActorSystem = _
}

class SbtRemotePlugin extends AbstractUIPlugin {
  import SbtRemotePlugin._

  private class ActorSystemActivator extends osgi.ActorSystemActivator {
    override def configure(context: BundleContext, sys: ActorSystem): Unit = {
      system = sys
    }

    override def getActorSystemName(context: BundleContext) =
      "SbtRemotePlugin"
  }

  private val systemActivator = new ActorSystemActivator
  @volatile private var listener: BundleListener = _

//  lazy val SbtLaunchJarLocation = fileLocationInBundle("/target/lib/sbt-launch.jar")
//  lazy val sbtProperties = urlOfFileZippedInBundle("sbt-server.properties", "/target/lib/client.jar")

  override def start(context: BundleContext) {
    super.start(context)
    plugin = this
    listener = new BundleListener {
      override def bundleChanged(e: BundleEvent) = {
        if (e.getBundle == getBundle && e.getType == BundleEvent.STARTED)
          systemActivator.start(context)
      }
    }
    context.addBundleListener(listener)
  }

  override def stop(context: BundleContext) {
    super.stop(context)
    plugin = null
    system = null
    if (listener != null) {
      context.removeBundleListener(listener)
      listener = null
    }
  }

  /** Returns the location on the file system of the bundled file. */
  private def fileLocationInBundle(filePath: String): File =
    new File(urlInBundle(filePath).getPath())

  /** Return the URL of the file on the file system of the bundled file. */
  private def urlInBundle(filePath: String): URL =
    FileLocator.toFileURL(FileLocator.find(getBundle, Path.fromPortableString(filePath), null))

  /** Return the URL on the file system of a file extracted from a bundled zip/jar file.
   *
   *  @param filePath path of the file in the zip file
   *  @param zipPath path of the zip file in the bundle
   */
  private def urlOfFileZippedInBundle(filePath: String, zipPath: String): URL = {
    val file = getBundle.getDataFile(filePath)

    if (!file.exists()) {
      // extract the file
      val zipFile = new ZipFile(fileLocationInBundle(zipPath))
      val zipEntry = zipFile.getEntry(filePath)

      val is = zipFile.getInputStream(zipEntry)
      val os = new FileOutputStream(file)
      while (is.available() > 0) {
        os.write(is.read)
      }
    }

    file.toURI.toURL()
  }

}
