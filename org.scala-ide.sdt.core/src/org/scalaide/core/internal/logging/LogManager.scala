package org.scalaide.core.internal.logging

import org.eclipse.jface.util.PropertyChangeEvent
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.logging.log4j.Log4JFacade
import org.scalaide.core.internal.logging.LoggingPreferenceConstants._
import org.scalaide.util.eclipse.SWTUtils
import org.scalaide.logging.HasLogger
import org.scalaide.logging.Level

object LogManager extends Log4JFacade with HasLogger {

  private def updateLogLevel(event: PropertyChangeEvent): Unit = {
    if (event.getProperty == LogLevel) {
      val level = event.getNewValue.asInstanceOf[String]
      setLogLevel(Level.withName(level))
    }
  }

  private def updateConsoleAppenderStatus(event: PropertyChangeEvent): Unit = {
    if (event.getProperty == IsConsoleAppenderEnabled) {
      val enable = event.getNewValue.asInstanceOf[Boolean]
      withoutConsoleRedirects {
        updateConsoleAppender(enable)
      }
    }
  }

  private def updateStdRedirectStatus(event: PropertyChangeEvent): Unit = {
    if (event.getProperty == RedirectStdErrOut) {
      val enable = event.getNewValue.asInstanceOf[Boolean]
      if (enable) redirectStdOutAndStdErr()
      else disableRedirectStdOutAndStdErr()

      // we need to restart the presentation compilers so that
      // the std out/err streams are refreshed by Console.in/out
      if (enable != event.getOldValue.asInstanceOf[Boolean])
        ScalaPlugin().resetAllPresentationCompilers()
    }
  }

  override protected def logFileName = "scala-ide.log"

  override def configure(logOutputLocation: String, preferredLogLevel: Level.Value): Unit = {
    import SWTUtils.fnToPropertyChangeListener

    super.configure(logOutputLocation, preferredLogLevel)

    val prefStore = ScalaPlugin().getPreferenceStore
    prefStore.addPropertyChangeListener(updateLogLevel _)
    prefStore.addPropertyChangeListener(updateConsoleAppenderStatus _)
    prefStore.addPropertyChangeListener(updateStdRedirectStatus _)

    if (prefStore.getBoolean(RedirectStdErrOut)) {
      redirectStdOutAndStdErr()
      ScalaPlugin().resetAllPresentationCompilers()
    }
  }

  override protected def setLogLevel(level: Level.Value): Unit = {
    super.setLogLevel(level)
    logger.info("Log level is `%s`".format(level))
  }

  override def currentLogLevel: Level.Value = {
    val levelName = ScalaPlugin().getPreferenceStore.getString(LogLevel)
    if (levelName.isEmpty) defaultLogLevel
    else Level.withName(levelName)
  }

  def defaultLogLevel: Level.Value = Level.WARN

  override def isConsoleAppenderEnabled: Boolean =
    ScalaPlugin().getPreferenceStore.getBoolean(IsConsoleAppenderEnabled)

  private def withoutConsoleRedirects(f: => Unit): Unit = {
    try {
      disableRedirectStdOutAndStdErr()
      f
    }
    finally { redirectStdOutAndStdErr() }
  }

  private def redirectStdOutAndStdErr(): Unit = {
    StreamRedirect.redirectStdOutput()
    StreamRedirect.redirectStdError()
  }

  private def disableRedirectStdOutAndStdErr(): Unit = {
    StreamRedirect.disableRedirectStdOutput()
    StreamRedirect.disableRedirectStdError()
  }
}
