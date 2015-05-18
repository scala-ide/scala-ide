package org.scalaide.logging

import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext
import org.scalaide.core.internal.logging.EclipseLogListener
import org.scalaide.core.internal.logging.LogManager

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
  override def start(context: BundleContext): Unit = {
    super.start(context)
    configure()
  }

  /** This method is called when the plug-in is stopped */
  override def stop(context: BundleContext): Unit = {
    super.stop(context);
    Option(eclipseLogListener) map (_.dispose())
  }

  /**
   * Configure logging, and install a listener which will forward all
   * log events sent to the Eclipse Logger to the plug-in's logger.
   */
  private def configure(): Unit = {
    LogManager.configure(getStateLocation.toOSString, LogManager.currentLogLevel)
    installEclipseLogForwarder()
  }

  private def installEclipseLogForwarder(): Unit = {
    assert(eclipseLogListener == null)
    val pluginLogger = LogManager.getLogger(getBundle.getSymbolicName)
    eclipseLogListener = new EclipseLogListener(getLog, pluginLogger)
  }
}
