package scala.tools.eclipse.debug

import org.eclipse.ui.plugin.AbstractUIPlugin
import org.eclipse.ui.IStartup
import org.osgi.framework.BundleContext

object ScalaDebugPlugin {
  var plugin: ScalaDebugPlugin = _

}

class ScalaDebugPlugin extends AbstractUIPlugin with IStartup {

  override def start(context: BundleContext) {
    super.start(context)
    ScalaDebugPlugin.plugin = this
    ScalaDebugger.init
  }

  /*
   * TODO: to move in start when launching a Scala application trigger the activation of this plugin.
   */
  def earlyStartup() {
    ScalaDebugger.init
  }

}