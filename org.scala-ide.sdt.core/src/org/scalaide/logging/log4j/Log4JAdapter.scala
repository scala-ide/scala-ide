package scala.tools.eclipse.logging.log4j

import scala.tools.eclipse.logging.Logger
import org.apache.log4j.{Logger => Log4JLogger}

private[log4j] object Log4JAdapter {
  def apply(name: String): Logger = {
    val logger = Log4JLogger.getLogger(name)
    new Log4JAdapter(logger)
  }
}

/** A thin wrapper around Log4J Logger. */
private class Log4JAdapter private (logger: Log4JLogger) extends Logger {

  // Mind that each method's implementation checks if the corresponding log's level
  // is enabled to avoid the cost of constructing the {{{message}}}. And that is the
  // reason for passing {{{message}}} by-name.


  def info(message: => Any) {
    if(logger.isInfoEnabled) logger.info(message)
  }

  def info(message: => Any, t: Throwable) {
    if(logger.isInfoEnabled) logger.info(message, t)
  }

  def debug(message: => Any) {
    if(logger.isDebugEnabled) logger.debug(message)
  }

  def debug(message: => Any, t: Throwable) {
    if(logger.isDebugEnabled) logger.debug(message, t)
  }

  def warn(message: => Any) {
    if(logger.isEnabledFor(org.apache.log4j.Level.WARN)) logger.warn(message)
  }

  def warn(message: => Any, t: Throwable) {
    if(logger.isEnabledFor(org.apache.log4j.Level.WARN)) logger.warn(message, t)
  }

  def error(message: => Any) {
    if(logger.isEnabledFor(org.apache.log4j.Level.ERROR)) logger.error(message)
  }

  def error(message: => Any, t: Throwable) {
    if(logger.isEnabledFor(org.apache.log4j.Level.ERROR)) logger.error(message, t)
  }

  def fatal(message: => Any) {
    if(logger.isEnabledFor(org.apache.log4j.Level.FATAL)) logger.fatal(message)
  }

  def fatal(message: => Any, t: Throwable) {
    if(logger.isEnabledFor(org.apache.log4j.Level.FATAL)) logger.fatal(message, t)
  }
}