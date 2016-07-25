package org.scalaide.sbt.core

import scala.util.Try

import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleEvent
import org.osgi.framework.BundleListener
import org.scalaide.core.internal.builder.BuildManagerFactory
import org.scalaide.sbt.core.builder.RemoteBuildManagerFactory

import akka.actor.ActorSystem
import akka.osgi

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

  override def start(context: BundleContext): Unit = {
    super.start(context)
    plugin = this
    listener = new BundleListener {
      override def bundleChanged(e: BundleEvent) = {
        if (e.getBundle == getBundle && e.getType == BundleEvent.STARTED)
          Try(getBundle.getBundleContext).map {
            systemActivator.start
          }
      }
    }
    context.addBundleListener(listener)
    import java.util.Hashtable
    context.registerService(classOf[BuildManagerFactory], new RemoteBuildManagerFactory, new Hashtable[String, String])
  }

  override def stop(context: BundleContext): Unit = {
    super.stop(context)
    systemActivator.stop(context)
    plugin = null
    system = null
    if (listener != null) {
      context.removeBundleListener(listener)
      listener = null
    }
  }

}
