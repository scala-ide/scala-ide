package org.scalaide.core.internal.logging

import org.eclipse.core.runtime.ILogListener
import org.eclipse.core.runtime.ILog
import org.eclipse.core.runtime.IStatus
import org.scalaide.logging.Logger

/**
 * This class forwards to the provided {{{Logger}}} all log requests occurring in the Eclipse
 * logging framework.
 * The idea is to collected all produced logs in a single place. */
class EclipseLogListener(log: ILog, logger: Logger) extends ILogListener {
  require(log != null, "<log> is null")
  require(logger != null, "<logger> is null")

  // Attach {{{this}}} listener to the eclipse logging framework (important!)
  log.addLogListener(this)

  /** Remove {{{this}}} listener from the Eclipse logging framework. */
  def dispose(): Unit = { log.removeLogListener(this) }

  override def logging(status: IStatus, plugin: String): Unit = {

    lazy val message = "%s - %s - %s - %s".format(plugin, status.getPlugin, status.getCode, status.getMessage)

    status.getSeverity() match {
      case IStatus.INFO => logger.info(message, status.getException)
      case IStatus.WARNING => logger.warn(message, status.getException)
      case IStatus.ERROR => logger.error(message, status.getException)
      case IStatus.CANCEL => logger.info(message, status.getException)
    }
  }
}
