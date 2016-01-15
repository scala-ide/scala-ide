package org.scalaide.core.internal.logging

import java.util.concurrent.atomic.AtomicReference
import org.scalaide.core.IScalaPlugin
import org.scalaide.util.eclipse.SWTUtils
import org.eclipse.core.runtime.ILog
import org.eclipse.core.runtime.IStatus
import org.scalaide.util.ui.DisplayThread
import scala.util.control.ControlThrowable
import org.eclipse.core.runtime.ILog
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.scalaide.logging.Logger

/** Use the `EclipseLogger` when you want to communicate with the user. Messages logged
 *  through the `EclipseLogger` are persisted in the Error Log.
 *
 *  This class is meant to be thread-safe, but it isn't because of a possible race condition existing
 *  in the Eclipse Logger. Indeed, the call to `ScalaPlugin.plugin.getLog()` isn't thread-safe, but
 *  there is really nothing we can do about it. The issue is that the `logs` map accessed in
 *  [[org.eclipse.core.internal.runtime.InternalPlatform.getLog(bundle)]] is not synchronized.
 *
 *  Maintainers should evolve this class by keeping in mind that the `EclipseLogger` will be
 *  accessed by different threads at the same time, so it is important to keep this class lock-free,
 *  and thread-safe (or maybe a better wording would be: as much thread-safe as it can be).
 *  And that is actually the main motivation for using an [[java.util.concurrent.atomic.AtomicReference]]
 *  for `lastCrash`. Also, note that declaring `lastCrash` as volatile (instead of using a
 *  [[java.util.concurrent.atomic.AtomicReference]]) would have made this class less correct (because
 *  volatile fields don't offer atomic operations such as `getAndSet`).
 */
object EclipseLogger extends Logger {
  private val pluginLogger: ILog = IScalaPlugin().getLog()

  private val lastCrash: AtomicReference[Throwable] = new AtomicReference

  override def trace(message: => Any): Unit = {
    info(message)
  }

  override def trace(message: => Any, t: Throwable): Unit = {
    info(message, t)
  }

  override def debug(message: => Any): Unit = {
    info(message)
  }

  override def debug(message: => Any, t: Throwable): Unit = {
    info(message, t)
  }

  override def info(message: => Any): Unit = {
    info(message, null)
  }

  override def info(message: => Any, t: Throwable): Unit = {
    log(IStatus.INFO, message, t)
  }

  override def warn(message: => Any): Unit = {
    warn(message, null)
  }

  override def warn(message: => Any, t: Throwable): Unit = {
    log(IStatus.WARNING, message, t)
  }

  override def error(message: => Any): Unit = {
    error(message, null)
  }

  override def error(message: => Any, t: Throwable): Unit = {
    log(IStatus.ERROR, message, t)
  }

  override def fatal(message: => Any): Unit = {
    error(message)
  }

  override def fatal(message: => Any, t: Throwable): Unit = {
    error(message, t)
  }

  private def log(severity: Int, message: => Any, t: Throwable): Unit = {
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
    if (message == null && exception == null)
      error("Error occurred in logger: message and exception are both null", new IllegalArgumentException)
    else {
      val status = new Status(severity, IScalaPlugin().getBundle.getSymbolicName, if (message == null) "" else message.toString, exception)
      if (IScalaPlugin().headlessMode) pluginLogger.log(status)
      else DisplayThread.asyncExec { pluginLogger.log(status) }
    }
  }
}
