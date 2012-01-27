package scala.tools.eclipse.logging

import org.eclipse.core.runtime.ILogListener
import org.eclipse.core.runtime.ILog
import org.eclipse.core.runtime.IStatus
import scala.collection.mutable.StringBuilder
import org.eclipse.core.runtime.Status

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
  def dispose() {
    Option(log) map {
      _.removeLogListener(this)
    }
  }

  override def logging(status: IStatus, plugin: String) {
    if (null == this.logger || null == status) return

    val message = "%s - %s - %s - %s".format(plugin, status.getPlugin, status.getCode, status.getMessage)

    val flog: (=> String, Throwable) => Unit = status.getSeverity() match {
      case IStatus.ERROR => logger.error
      case IStatus.WARNING => logger.warn
      case IStatus.INFO => logger.info
      case IStatus.CANCEL => logger.info
    }
    
    val exception = Option(status.getException).getOrElse {
      val ex = new Exception
      ex.fillInStackTrace
      ex
    }
    
    flog(message, status.getException)
  }
}