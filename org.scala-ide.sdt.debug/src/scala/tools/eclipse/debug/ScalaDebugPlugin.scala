package scala.tools.eclipse.debug

import org.eclipse.ui.plugin.AbstractUIPlugin
import org.eclipse.ui.IStartup
import org.osgi.framework.BundleContext
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.IStatus

object ScalaDebugPlugin {
  @volatile var plugin: ScalaDebugPlugin = _

  def id = plugin.getBundle().getSymbolicName()

  def wrapInCoreException(message: String, e: Throwable) = new CoreException(wrapInErrorStatus(message, e))

  def wrapInErrorStatus(message: String, e: Throwable) = new Status(IStatus.ERROR, ScalaDebugPlugin.id, message, e)

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
