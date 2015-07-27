package org.scalaide.debug.internal

import com.sun.jdi.event.Event
import scala.concurrent.Future
import com.sun.jdi.request.EventRequest

trait JdiEventDispatcher {
  def register(observer: JdiEventReceiver, request: EventRequest): Unit
  def unregister(request: EventRequest): Unit
}

trait JdiEventReceiver {
  def handle(event: Event): Future[Boolean]
}
