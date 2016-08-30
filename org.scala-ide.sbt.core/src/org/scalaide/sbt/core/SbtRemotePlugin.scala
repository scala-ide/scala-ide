package org.scalaide.sbt.core

import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext

import akka.actor.ActorSystem

object SbtRemotePlugin {
  @volatile private var plugin: SbtRemotePlugin = _

  def apply(): SbtRemotePlugin = plugin

  val ActorSystemName = "SbtRemotePlugin"
}

class SbtRemotePlugin extends AbstractUIPlugin {
  import SbtRemotePlugin._

  lazy val system: ActorSystem = ActorSystem(ActorSystemName)

  override def start(context: BundleContext): Unit = {
    super.start(context)
    plugin = this
  }

  override def stop(context: BundleContext): Unit = {
    super.stop(context)
    system.terminate()
    plugin = null
  }
}
