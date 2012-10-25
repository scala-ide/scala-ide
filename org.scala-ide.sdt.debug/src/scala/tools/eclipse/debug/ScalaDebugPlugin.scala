package scala.tools.eclipse.debug

import org.eclipse.ui.plugin.AbstractUIPlugin
import org.eclipse.ui.IStartup
import org.osgi.framework.BundleContext

object ScalaDebugPlugin {
  @volatile var plugin: ScalaDebugPlugin = _

}

class ScalaDebugPlugin extends AbstractUIPlugin with IStartup {

  override def start(context: BundleContext) {
    super.start(context)
    ScalaDebugPlugin.plugin = this
    ScalaDebugger.init()
  }

  override def stop(context: BundleContext) {
    try super.stop(context)
    finally ScalaDebugPlugin.plugin = null
  }
  
  /*
   * TODO: to move in start when launching a Scala application trigger the activation of this plugin.
   */
  override def earlyStartup() {
    ScalaDebugger.init()
  }

}