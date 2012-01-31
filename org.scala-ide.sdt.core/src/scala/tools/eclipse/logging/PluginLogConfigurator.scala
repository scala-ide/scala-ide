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
trait PluginLogConfigurator extends AbstractUIPlugin with HasLogger {

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
    LogManager.configure(getStateLocation.toOSString, LogManager.currentLogLevel)
    installEclipseLogEventsListener()
    redirectStdOutAndStdErr()
  }

  private def installEclipseLogEventsListener() {
    assert(eclipseLogListener == null)
    val pluginLogger = LogManager.getLogger(getBundle.getSymbolicName)
    eclipseLogListener = new EclipseLogListener(getLog, pluginLogger)
  }

  private def redirectStdOutAndStdErr() {
    StreamRedirect.redirectStdOutput(logger)
    StreamRedirect.redirectStdError(logger)
  }
}

private[logging] object StreamRedirect {
  import java.io.{ OutputStream, PrintStream }

  private var isStdOutRedirected = false
  private var isStdErrRedirected = false

  def redirectStdOutput(logger: Logger): Unit = synchronized {
    if (!isStdOutRedirected) {
      val outStream = redirect(msg => logger.info(msg))
      System.setOut(outStream)
      isStdOutRedirected = true
    }
  }

  def redirectStdError(logger: Logger): Unit = synchronized {
    if (!isStdErrRedirected) {
      val errStream = redirect(msg => logger.error(msg))
      System.setErr(errStream)
      isStdErrRedirected = true
    }
  }

  private def redirect(to: Any => Unit): PrintStream = 
    new PrintStream(new Redirect(to), /*autoFlush = */true)

  private class Redirect(to: Any => Unit) extends OutputStream {
    override def write(b: Int) {
      to(String.valueOf(b.asInstanceOf[Char]))
    }

    override def write(b: Array[Byte], off: Int, len: Int) {
      to(new String(b, off, len));
    }

    override def write(b: Array[Byte]) {
      write(b, 0, b.size);
    }
  }
}