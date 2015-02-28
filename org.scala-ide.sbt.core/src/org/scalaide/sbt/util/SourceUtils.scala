package org.scalaide.sbt.util

import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Source
import scala.concurrent.Promise
import scala.concurrent.Future
import java.io.File
import sbt.Attributed

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

}
