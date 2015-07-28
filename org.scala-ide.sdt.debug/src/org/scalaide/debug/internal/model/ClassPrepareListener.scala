package org.scalaide.debug.internal.model

import com.sun.jdi.event.ClassPrepareEvent
import scala.concurrent.Future

trait ClassPrepareListener {
  def notify(event: ClassPrepareEvent): Future[Unit]
}
