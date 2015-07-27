package org.scalaide.debug.internal.model

import com.sun.jdi.event.ClassPrepareEvent
import scala.concurrent.Await
import scala.concurrent.Future
import scala.util.Try

trait ClassPrepareListener {
  import scala.concurrent.duration._
  val Timeout = 500 millis
  type Timeouted = Boolean

  def notify(event: ClassPrepareEvent): Timeouted = {
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.TimeoutException

    Try {
      Await.ready(Future { consume(event) }, Timeout)
    }.map { _ =>
      false
    }.recover {
      case e: TimeoutException => true
    }.get
  }

  protected def consume(event: ClassPrepareEvent): Unit
}
