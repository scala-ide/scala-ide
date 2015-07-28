package org.scalaide.debug.internal.model

import scala.util.Try
import scala.concurrent.Await
import scala.concurrent.Future
import org.scalaide.logging.HasLogger

object SyncCall extends HasLogger {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  val Timeout = 500 millis

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

  def timeout[T](f: => Future[T]): Boolean = {
    import scala.concurrent.TimeoutException
    Try {
      Await.ready(f, Timeout)
    }.map { _ =>
      false
    }.recover {
      case e: TimeoutException => true
    }.get
  }
}
