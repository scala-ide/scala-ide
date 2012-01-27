package scala.tools.eclipse.logging

import scala.tools.eclipse.logging.log4j.Log4JFacade
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jdt.internal.ui.viewsupport.IProblemChangedListener
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jdt.ui.PreferenceConstants

object LogManager extends Log4JFacade with HasLogger {
  import LogPreferenceConstants._

  override protected val logFileName = "scala-ide.log"
  
  private class ConsoleAppenderEnablerPropertyListener extends IPropertyChangeListener {
    override def propertyChange(event: PropertyChangeEvent) {
      if (event.getProperty == IsConsoleAppenderEnabled) {
        val enable = event.getNewValue.asInstanceOf[Boolean]
        if (enable) updateConsoleAppender(enable)
      }
    }
  }

  override def configure(preferredLogLevel: Level.Value) {
    PreferenceConstants.getPreferenceStore.addPropertyChangeListener(new ConsoleAppenderEnablerPropertyListener)
    super.configure(preferredLogLevel)
  }

  override def setLogLevel(level: Level.Value) {
    super.setLogLevel(level)
    ScalaPlugin.plugin.getPreferenceStore.setValue(LogLevel, level.toString)
    ScalaPlugin.plugin.savePluginPreferences
    logger.info("Log level is `%s`".format(level))
  }

  override def currentLogLevel: Level.Value = {
    val levelName = ScalaPlugin.plugin.getPreferenceStore.getString(LogLevel)
    if (levelName.isEmpty) defaultLogLevel
    else Level.withName(levelName)
  }

  private def defaultLogLevel: Level.Value = Level.WARN

  override def isConsoleAppenderEnabled: Boolean =
    PreferenceConstants.getPreferenceStore.getBoolean(IsConsoleAppenderEnabled)

}

object LogPreferenceConstants {
  private final val Prefix = "log-pref." 
  final val LogLevel = Prefix + "level"
  final val IsConsoleAppenderEnabled = Prefix + "isConsoleAppenderEnabled"
}