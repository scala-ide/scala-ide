package scala.tools.eclipse.logging

import java.net.URL
import scala.util.control.Exception.Catch
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext

/**
 * This trait can be used to configure an Eclipse plug-in to use an external logging framework (such as Log4J).
 * When mixed-in, the following steps are executed:
 *
 * - Programmatically configure the external logging framework.
 * - Install a listener to forward to the external logging framework all log Events occurring in the Eclipse Log framework.
 * - Redirect to the external logging framework all messages sent to the Standard Output/Error.
 */
trait PluginLogConfigurator extends AbstractUIPlugin {

  /** Listen to log events occurring in the Eclipse log framework and redirect them to the plug-in logger. */
  @volatile private var eclipseLogListener: EclipseLogListener = _

  /** This method is called upon plug-in activation. */
  override def start(context: BundleContext) {
    super.start(context)
    configure()
  }

  /** This method is called when the plug-in is stopped */
  override def stop(context: BundleContext) {
    super.stop(context);
    Option(eclipseLogListener) map (_.dispose())
  }

  /**
   * Configure logging, and install a listener which will forward all
   * log events sent to the Eclipse Logger to the plug-in's logger.
   */
  private def configure() {
    LogManager.configure(getStateLocation.toOSString, LogManager.currentLogLevel)
    installEclipseLogForwarder()
  }

  private def installEclipseLogForwarder() {
    assert(eclipseLogListener == null)
    val pluginLogger = LogManager.getLogger(getBundle.getSymbolicName)
    eclipseLogListener = new EclipseLogListener(getLog, pluginLogger)
  }
}