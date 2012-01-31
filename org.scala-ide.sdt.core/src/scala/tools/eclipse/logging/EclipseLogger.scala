package scala.tools.eclipse.logging

import org.eclipse.core.runtime.Status
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.runtime.{ ILog, IStatus }
import scala.util.control.ControlThrowable

private[logging] object EclipseLogger extends Logger {
  private final val pluginLogger: ILog = ScalaPlugin.plugin.getLog

  def debug(message: => Any) {
    info(message)
  }

  def debug(message: => Any, t: Throwable) {
    info(message, t)
  }

  def info(message: => Any) {
    info(message, null)
  }

  def info(message: => Any, t: Throwable) {
    log(IStatus.INFO, message, t)
  }

  def warn(message: => Any) {
    warn(message, null)
  }

  def warn(message: => Any, t: Throwable) {
    log(IStatus.WARNING, message, t)
  }

  def error(message: => Any) {
    error(message, null)
  }

  def error(message: => Any, t: Throwable) {
    log(IStatus.ERROR, message, t)
  }

  def fatal(message: => Any) {
    error(message)
  }

  def fatal(message: => Any, t: Throwable) {
    error(message, t)
  }
  
  private def log(severity: Int, message: => Any, t: Throwable = null) {
    pluginLogger.log(createStatus(severity, message, t))
    t match {
      case ce: ControlThrowable =>
        pluginLogger.log(createStatus(IStatus.ERROR, "Incorrectly logged ControlThrowable: " + ce.getClass.getSimpleName + "(" + ce.getMessage + ")", t))
      case _ => () // do nothing
    }
  }

  private def createStatus(severity: Int, message: Any, exception: Throwable): Status = {
    new Status(severity, ScalaPlugin.plugin.getBundle.getSymbolicName, message.toString, exception)
  }
}