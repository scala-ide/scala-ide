package org.scalaide.debug.internal.model

import scala.util.Try
import scala.concurrent.Await
import scala.concurrent.Future
import org.scalaide.logging.HasLogger

object SyncCall extends HasLogger {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  val Timeout = 500 millis
  type TimeoutOccurred = Boolean

  def result[T](f: => T): Option[T] = {
    import scala.concurrent.TimeoutException

    Try {
      Await.result(Future { f }, Timeout)
    }.map {
      Option.apply
    }.recover {
      case e: TimeoutException =>
        logger.info("TIMEOUT waiting while 'f' called")
        None
    }.get
  }

  def ready(f: => Unit): TimeoutOccurred = {
    import scala.concurrent.TimeoutException

    Try {
      Await.ready(Future { f }, Timeout)
    }.map { _ =>
      false
    }.recover {
      case e: TimeoutException => true
    }.get
  }
}
