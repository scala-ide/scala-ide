package org.scalaide.debug.internal

import com.sun.jdi.event.Event
import scala.concurrent.Future
import com.sun.jdi.request.EventRequest
import scala.concurrent.ExecutionContext

trait JdiEventDispatcher {
  def register(observer: JdiEventReceiver, request: EventRequest): Unit
  def unregister(request: EventRequest): Unit
}

trait JdiEventReceiver {
  type StaySuspended = Boolean

  final def handle(event: Event)(implicit ec: ExecutionContext): Future[StaySuspended] = Future {
    innerHandle.orElse[Event, StaySuspended] {
      case _ => false
    }(event)
  }

  protected def innerHandle: PartialFunction[Event, StaySuspended]
}
