package org.scalaide.sbt.util

import scala.concurrent.Future
import scala.concurrent.Promise

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Source

object SourceUtils {

  implicit class RichSource[A](src: Source[A]) {
    def firstFuture(implicit materializer: FlowMaterializer): Future[A] = {
      val p = Promise[A]
      src.take(1).runForeach { elem ⇒
        p.trySuccess(elem)
      }
      p.future
    }
  }

  /**
   * Creates a `Source[A]` from an arbitrary event stream. The arriving events
   * need to be sent to `subs`, which needs to return a function that allows the
   * `Source` to cancel the event stream.
   */
  def fromEventStream[A](subs: Subscriber[_ >: A] ⇒ () ⇒ Unit): Source[A] = {
    Source(new Publisher[A] {
      var cancellation: () ⇒ Unit = _
      override def subscribe(s: Subscriber[_ >: A]): Unit = {
        s.onSubscribe(new Subscription {
          def request(n: Long): Unit = ()
          def cancel(): Unit = cancellation()
        })
        cancellation = subs(s)
      }
    })
  }
}
