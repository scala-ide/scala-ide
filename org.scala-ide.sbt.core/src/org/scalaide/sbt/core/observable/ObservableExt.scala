package org.scalaide.sbt.core.observable

import rx.lang.scala.Observable
import scala.concurrent.Promise
import rx.lang.scala.Subscription
import scala.concurrent.Future
import rx.lang.scala.Observer
import scala.concurrent.ExecutionContext.Implicits.global

object ObservableExt {

  /**
   * Based on the Observable.replay(int) method in RxJava, missing in RxScala.
   * With a direct call to connect(), because RxScala is missing the ConnectableObservable trait.
   */
  def replay[T](observable: Observable[T], n: Int): Observable[T] = {
    import rx.lang.scala.JavaConversions._
    val res = toJavaObservable[T](observable).replay(n)
    res.connect
    res
  }

  // TODO: cancellable future?
  def firstFuture[T](observable: Observable[T]): Future[T] = {
    val promise = Promise[T]
    val subscription = observable {
      new Observer[T]() {
        override def onNext(value: T) {
          promise.trySuccess(value)
        }
        override def onError(error: Throwable) {
          promise.tryFailure(error)
        }
        override def onCompleted() {
          promise.tryFailure(new NoValueException())
        }
      }
    }
    val future = promise.future

    future.foreach { v => subscription.unsubscribe() }

    future
  }

  class NoValueException extends Exception

}