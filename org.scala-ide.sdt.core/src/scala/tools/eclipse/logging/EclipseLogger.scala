package scala.tools.eclipse.logging

import org.eclipse.core.runtime.Status
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.util.SWTUtils
import org.eclipse.core.runtime.{ ILog, IStatus }
import scala.util.control.ControlThrowable
import java.util.concurrent.atomic.AtomicReference

/** Use the `EclipseLogger` when you want to communicate with the user. Messages logged
 *  through the `EclipseLogger` are persisted in the Error Log.
 *
 *  This class is meant to be thread-safe, but it isn't because of a possible race condition existing
 *  in the Eclipse Logger. Indeed, the call to `ScalaPlugin.plugin.getLog()` isn't thread-safe, but
 *  there is really nothing we can do about it. The issue is that the `logs` map accessed in
 *  [[org.eclipse.core.internal.runtime.InternalPlatform.getLog(bundle)]] is not synchronized.
 *
 *  Mantainers should evolve this class by keeping in mind that the `EclipseLogger` will be
 *  accessed by different threads at the same time, so it is important to keep this class lock-free,
 *  and thread-safe (or maybe a better wording would be: as much thread-safe as it can be).
 *  And that is actually the main motivation for using an [[java.util.concurrent.atomic.AtomicReference]]
 *  for `lastCrash`. Also, note that declaring `lastCrash` as volatile (instead of using a
 *  [[java.util.concurrent.atomic.AtomicReference]]) would have made this class less correct (because
 *  volatile fields don't offer atomic operations such as `getAndSet`).
 */
private[logging] object EclipseLogger extends Logger {
  private val pluginLogger: ILog = ScalaPlugin.plugin.getLog()

  private val lastCrash: AtomicReference[Throwable] = new AtomicReference

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
    if (t == null) logInUiThread(severity, message, t)
    else {
      // this is an optimization to log the exception at most once if the same exception is being re-thrown several times.
      val oldValue = lastCrash.getAndSet(t)
      if (oldValue != t) {
        logInUiThread(severity, message, t)
        t match {
          // `ControlThrowable` should never (ever!) be caught by user code. If that happens, generate extra noise.
          case ce: ControlThrowable =>
            logInUiThread(IStatus.ERROR, "Incorrectly logged ControlThrowable: " + ce.getClass.getSimpleName + "(" + ce.getMessage + ")", t)
          case _ => () // do nothing
        }
      }
    }
  }

  // Because of a potential deadlock in the Eclipse internals (look at #1000914), the log action need to be executed in the UI thread.
  private def logInUiThread(severity: Int, message: Any, exception: Throwable): Unit = {
    val status = new Status(severity, ScalaPlugin.plugin.getBundle.getSymbolicName, message.toString, exception)
    if (ScalaPlugin.plugin.headlessMode) pluginLogger.log(status)
    else SWTUtils.asyncExec { pluginLogger.log(status) }
  }
}
