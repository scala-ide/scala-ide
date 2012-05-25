package scala.tools.eclipse.buildmanager.sbtintegration

import scala.tools.eclipse.buildmanager.BuildReporter
import scala.tools.nsc.util.{ Position, NoPosition, FakePos }
import scala.collection.mutable
import scala.tools.eclipse.logging.HasLogger

/** An Sbt logger that forwards to our logging infrastructure. */
class SbtBuildLogger(underlying: BuildReporter) extends sbt.Logger with HasLogger {
  def trace(t: => Throwable) {
    logger.debug("Exception during Sbt compilation", t)
  }
  
  def success(message: => String) { logger.info("success: " + message) }

  def log(level: sbt.Level.Value, message: => String) {
    import sbt.Level.{ Info, Warn, Error, Debug }
    level match {
      case Info  => logger.info(message)
      case Warn  => logger.warn(message)
      case Error => logger.error(message)
      case Debug => logger.debug(message)
    }
  }
}
