package org.scalaide.debug.internal.model

import scala.util.Try
import scala.concurrent.Await
import scala.concurrent.Future
import org.scalaide.logging.HasLogger

object SyncCall extends HasLogger {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  val Timeout = 500 millis

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

  def timeoutWithResult[T](f: => Future[T]): Option[T] = {
    import scala.concurrent.TimeoutException
    Try {
      Await.result(f, Timeout)
    }.map {
      Option.apply
    }.recover {
      case e: TimeoutException => None
    }.get
  }
}
