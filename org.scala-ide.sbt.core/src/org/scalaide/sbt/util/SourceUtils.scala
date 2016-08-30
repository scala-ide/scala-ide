package org.scalaide.sbt.util

import scala.concurrent.Future

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

object SourceUtils {

  implicit class RichSource[A, B](src: Source[A, B]) {

    /**
     * Returns the first element of the source and cancels the source
     * afterwards.
     */
    def firstFuture(implicit materializer: Materializer): Future[A] =
      src.runWith(Sink.head)
  }

  /**
   * Creates a `Source[A]` from an arbitrary event stream. The arriving events
   * need to be sent to `subs`, which needs to return a function that allows the
   * `Source` to cancel the event stream.
   */
  def fromEventStream[A](subs: Subscriber[_ >: A] ⇒ () ⇒ Unit): Source[A, NotUsed] = {
    Source.fromPublisher(new Publisher[A] {
      var cancellation: () ⇒ Unit = _
      override def subscribe(s: Subscriber[_ >: A]): Unit = {
        s.onSubscribe(new Subscription {
          override def request(n: Long): Unit = ()
          override def cancel(): Unit = cancellation()
        })
        cancellation = subs(s)
      }
    })
  }
}
