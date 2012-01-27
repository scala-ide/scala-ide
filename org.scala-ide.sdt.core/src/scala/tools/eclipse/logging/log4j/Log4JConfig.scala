package scala.tools.eclipse.logging.log4j

import org.apache.log4j.{ Level => Log4JLevel }
import org.apache.log4j.RollingFileAppender
import org.apache.log4j.PatternLayout
import org.apache.log4j.ConsoleAppender

/** This class is used to programmatically configure log4j. */
private[log4j] class Log4JConfig(logManager: Log4JFacade) {

  private lazy val layout = new PatternLayout("%d %5p [%t] - %c - %m%n")
  
  private lazy val consoleAppender = new ConsoleAppender(layout, ConsoleAppender.SYSTEM_OUT)
    
  def configure(logFileName: String, preferredLogLevel: Log4JLevel) {
    val appender = new RollingFileAppender(layout, logFileName, true)

    val rootLogger = logManager.getRootLogger
    rootLogger.setLevel(preferredLogLevel)
    rootLogger.addAppender(appender)
    
    if(logManager.isConsoleAppenderEnabled) 
      addConsoleAppender()
  }
  
  def addConsoleAppender() {
    val rootLogger = logManager.getRootLogger
    rootLogger.addAppender(consoleAppender)
  }
  
  def removeConsoleAppender() {
    val rootLogger = logManager.getRootLogger
    rootLogger.removeAppender(consoleAppender)
  }
}