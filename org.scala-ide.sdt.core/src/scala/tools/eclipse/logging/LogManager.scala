package scala.tools.eclipse.logging

import scala.tools.eclipse.logging.log4j.Log4JFacade
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jdt.internal.ui.viewsupport.IProblemChangedListener
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jdt.ui.PreferenceConstants
import scala.tools.eclipse.util.SWTUtils
import java.io.File

object LogManager extends Log4JFacade with HasLogger {
  import ui.properties.LoggingPreferenceConstants._

  private def updateLogLevel: IPropertyChangeListener = {
    SWTUtils.fnToPropertyChangeListener { event =>
      if (event.getProperty == LogLevel) {
        val level = event.getNewValue.asInstanceOf[String]
        setLogLevel(Level.withName(level))
      }
    }
  }

  private def updateConsoleAppenderStatus: IPropertyChangeListener = {
    SWTUtils.fnToPropertyChangeListener { event =>
      if (event.getProperty == IsConsoleAppenderEnabled) {
        val enable = event.getNewValue.asInstanceOf[Boolean]
        updateConsoleAppender(enable)
      }
    }
  }

  override protected def logFileName = "scala-ide.log"

  override def configure(logOutputLocation: String, preferredLogLevel: Level.Value) {
    super.configure(logOutputLocation, preferredLogLevel)
    ScalaPlugin.plugin.getPreferenceStore.addPropertyChangeListener(updateLogLevel)
    ScalaPlugin.plugin.getPreferenceStore.addPropertyChangeListener(updateConsoleAppenderStatus)
  }

  override protected def setLogLevel(level: Level.Value) {
    super.setLogLevel(level)
    logger.info("Log level is `%s`".format(level))
  }

  override def currentLogLevel: Level.Value = {
    val levelName = ScalaPlugin.plugin.getPreferenceStore.getString(LogLevel)
    if (levelName.isEmpty) defaultLogLevel
    else Level.withName(levelName)
  }

  private[logging] def defaultLogLevel: Level.Value = Level.WARN

  override def isConsoleAppenderEnabled: Boolean =
    ScalaPlugin.plugin.getPreferenceStore.getBoolean(IsConsoleAppenderEnabled)
}