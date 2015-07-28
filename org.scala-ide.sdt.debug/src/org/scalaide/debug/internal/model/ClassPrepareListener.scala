package org.scalaide.debug.internal.model

import com.sun.jdi.event.ClassPrepareEvent

trait ClassPrepareListener {
  def notify(event: ClassPrepareEvent): Boolean = SyncCall.ready(consume(event))

  protected def consume(event: ClassPrepareEvent): Unit
}
