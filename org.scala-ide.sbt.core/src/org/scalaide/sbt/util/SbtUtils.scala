package org.scalaide.sbt.util

import scala.concurrent.ExecutionContext

import akka.stream.scaladsl.Source
import sbt.client.SbtClient
import sbt.protocol.Event
import akka.NotUsed

object SbtUtils {

  object RunOnSameThreadContext extends ExecutionContext {
    override def execute(r: Runnable) =
      r.run()
    override def reportFailure(t: Throwable) =
      println(s"error in RunOnSameThreadContext: ${t.getMessage}")
  }

  /**
   * Retrieves a `Source` that gets its elements from `client`. All events that
   * are subtypes of `A` are fed into the returned `Source`.
   */
  def protocolEventWatcher[A <: Event : reflect.ClassTag](client: SbtClient)(implicit ctx: ExecutionContext): Source[A, NotUsed] = {
    SourceUtils.fromEventStream { subs ⇒
      val cancellation = client handleEvents {
        case e: A ⇒ subs.onNext(e)
        case _    ⇒
      }
      () ⇒ cancellation.cancel()
    }
  }
}
