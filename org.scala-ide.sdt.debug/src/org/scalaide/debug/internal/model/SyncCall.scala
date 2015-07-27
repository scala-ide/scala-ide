package org.scalaide.debug.internal.model

import scala.util.Try
import scala.concurrent.Await
import scala.concurrent.Future
import org.scalaide.logging.HasLogger

object SyncCall extends HasLogger {
  import scala.concurrent.duration._
  val Timeout = 500 millis

  def apply[T](f: => T): Option[T] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.TimeoutException

    Try {
      Await.result(Future { f }, Timeout)
    }.map {
      Option.apply
    }.recover {
      case e: TimeoutException =>
        logger.info("TIMEOUT waiting for debug cache actor in getLoadedNestedTypes")
        None
    }.get
  }
}
