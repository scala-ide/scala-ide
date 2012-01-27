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
    log(IStatus.INFO, message, Option(t))
  }

  def warn(message: => Any) {
    warn(message, null)
  }

  def warn(message: => Any, t: Throwable) {
    log(IStatus.WARNING, message, Option(t))
  }

  def error(message: => Any) {
    error(message, null)
  }

  def error(message: => Any, t: Throwable) {
    log(IStatus.ERROR, message, Option(t))
  }

  def fatal(message: => Any) {
    error(message)
  }

  def fatal(message: => Any, t: Throwable) {
    error(message, t)
  }
  
  private def log(severity: Int, message: => Any, t: Option[Throwable] = None) {
    lazy val t1 = { val ex = new Exception; ex.fillInStackTrace; ex }
    pluginLogger.log(createStatus(severity, message, t.getOrElse(t1)))
    t.collect {
      case ce: ControlThrowable =>
        val t2 = { val ex = new Exception; ex.fillInStackTrace; ex }
        error("Incorrectly logged ControlThrowable: " + ce.getClass.getSimpleName + "(" + ce.getMessage + ")", t2)
    }
  }

  private def createStatus(severity: Int, message: Any, exception: Throwable): Status = {
    new Status(severity, ScalaPlugin.plugin.getBundle.getSymbolicName, message.toString, exception)
  }
}