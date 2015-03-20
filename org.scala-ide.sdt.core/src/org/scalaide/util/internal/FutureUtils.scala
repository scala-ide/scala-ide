package org.scalaide.util.internal

import scala.concurrent._, ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

object FutureUtils {

  /**
   * Returns a `Future`, which is completed after a span of time that is passed
   * as `timeout`. `f` is the function that is executed in the `Future` and
   * whose result may complete the future. If `f` does not complete in time, the
   * returned `Future` is completed with a `TimeoutException`.
   */
  object TimeoutFuture {
    def apply[A](timeout: FiniteDuration)(f: => A): Future[A] = {
      val p = Promise[A]()

      val resFuture = Future(p success f)
      val delayFuture = Future.delay(timeout)
      delayFuture.onComplete(_ => p.tryFailure(new TimeoutException))
      Future.firstCompletedOf(List(resFuture, delayFuture))

      p.future
    }
  }

  /**
   * Adds extensions to the `Future` companion object.
   */
  implicit class FutureExtensions(private val f: Future.type) extends AnyVal {

    /**
     * Returns a `Future` that is never completed.
     */
    def never[A]: Future[A] = Promise[A]().future

    /**
     * Returns a `Future` with a unit value that is completed after time `time`.
     */
    def delay(time: Duration): Future[Unit] = Future {
      blocking { Try(Await.ready(never[Unit], time)) }
    }
  }
}
