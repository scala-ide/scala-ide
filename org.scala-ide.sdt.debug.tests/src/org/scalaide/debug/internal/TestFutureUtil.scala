package org.scalaide.debug.internal

import scala.concurrent.Future
import scala.concurrent.Await
import scala.util.Try
import org.junit.Assert
import scala.concurrent.Promise

object TestFutureUtil {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  val DefaultTimeout = 200 millis

  def whenReady[T](future: => Future[T], timeout: Duration = DefaultTimeout)(withal: T => Unit): Unit =
    withal(Await.result(future, timeout))

  def tryAsResult[T](future: => Future[T]): Future[Try[T]] = {
    val p = Promise[Try[T]]
    future onComplete { t =>
      p success(t);
    }
    p.future
  }

  def waitForConditionOrTimeout(cond: => Boolean, timeout: Duration = DefaultTimeout): Unit = {
    Try(Assert.assertTrue(Await.result(Future { cond }, timeout))) recover {
      case all => Assert.fail(s"condition '$cond' timed out")
    }
  }
}
