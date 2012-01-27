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
  private var eclipseLogListener: EclipseLogListener = _

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
   * Configure logging, redirects standard output/error in to the plug-in's logger and also forward 
   * all log event happening in the Eclipse Error Log view in to the plug-in's logger.
   */
  private def configure() {
    LogManager.setLogFileLocation(getStateLocation.toOSString)
    LogManager.configure(LogManager.currentLogLevel)
    installEclipseLogEventsListener()
    redirectStdOutAndStdErr()
  }

  private def installEclipseLogEventsListener() {
    assert(eclipseLogListener == null)
    val pluginLogger = LogManager.getLogger(getBundle.getSymbolicName)
    eclipseLogListener = new EclipseLogListener(getLog, pluginLogger)
  }

  private def redirectStdOutAndStdErr() {
    stream.StdOutRedirect.enable()
    stream.StdErrRedirect.enable()
  }
}