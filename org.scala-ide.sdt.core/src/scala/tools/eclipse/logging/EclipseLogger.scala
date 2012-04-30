package scala.tools.eclipse.logging

import org.eclipse.core.runtime.Status
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.util.SWTUtils
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
    // Because of a potential deadlock in the Eclipse internals (look at #1000914), the log action need to be executed in the UI thread.
    def log(status: Status) = 
      if (ScalaPlugin.plugin.headlessMode) pluginLogger.log(status)
      else SWTUtils.asyncExec { pluginLogger.log(status) }
    
    log(createStatus(severity, message, t))
    t match {
      // `ControlThrowable` should never (ever!) be caught by user code. If that happens, generate extra noise. 
      case ce: ControlThrowable =>
        log(createStatus(IStatus.ERROR, "Incorrectly logged ControlThrowable: " + ce.getClass.getSimpleName + "(" + ce.getMessage + ")", t))
      case _ => () // do nothing
    }
  }

  private def createStatus(severity: Int, message: Any, exception: Throwable): Status = {
    new Status(severity, ScalaPlugin.plugin.getBundle.getSymbolicName, message.toString, exception)
  }
}
