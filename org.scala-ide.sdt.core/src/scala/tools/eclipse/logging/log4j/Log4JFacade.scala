package scala.tools.eclipse.logging
package log4j

import java.net.URL
import scala.tools.eclipse.logging.Logger
import org.apache.log4j.spi.HierarchyEventListener
import org.apache.log4j.Appender
import org.apache.log4j.Category
import org.apache.log4j.FileAppender
import org.apache.log4j.{ Level => Log4JLevel }
import org.apache.log4j.{ Logger => Log4JLogger }
import org.apache.log4j.LogManager
import org.apache.log4j.PropertyConfigurator
import scala.tools.eclipse.logging.Level
import java.io.File

/**
 * This class offers a facade over Log4J and exposes only the Log4J's
 * features that we currently use/need (which, in practice, are not that many).
 */
private[logging] abstract class Log4JFacade {

  private final val config: Log4JConfig = new Log4JConfig(this)

  private var _logFile: File = _

  def logFile: File = synchronized { _logFile }

  protected def logFileName: String

  def configure(logOutputLocation: String, preferredLogLevel: Level.Value) {
    synchronized {
      _logFile = new File(logOutputLocation + java.io.File.separator + logFileName)
    }
    config.configure(_logFile, toLog4JLevel(preferredLogLevel))
  }

  private[log4j] def getRootLogger: Log4JLogger = LogManager.getRootLogger

  def currentLogLevel: Level.Value

  /** Programmatically change the root logger's log level. */
  protected def setLogLevel(level: Level.Value) {
    val log4JLevel = toLog4JLevel(level)
    LogManager.getRootLogger.setLevel(log4JLevel)
  }

  private def toLog4JLevel(level: Level.Value): Log4JLevel = level match {
    case Level.DEBUG => Log4JLevel.DEBUG
    case Level.INFO => Log4JLevel.INFO
    case Level.WARN => Log4JLevel.WARN
    case Level.ERROR => Log4JLevel.ERROR
    case Level.FATAL => Log4JLevel.FATAL
  }

  protected def updateConsoleAppender(enable: Boolean) {
    if (enable) config.addConsoleAppender()
    else config.removeConsoleAppender()
  }

  def isConsoleAppenderEnabled: Boolean

  /** Factory for Log4J logger's instance.*/
  private object Logger {
    def apply(clazz: Class[_]): Logger = {
      val name = if (clazz.isAnonymousClass()) clazz.getName else clazz.getSimpleName
      this(name)
    }

    def apply(name: String): Logger = log4j.Log4JAdapter(name)
  }

  /** Get the logger instance for the passed {{{clazz}}}. */
  def getLogger(clazz: Class[_]): Logger = Logger(clazz)

  /** Get the logger instance for the passed {{{name}}}. */
  def getLogger(name: String): Logger = Logger(name)
}