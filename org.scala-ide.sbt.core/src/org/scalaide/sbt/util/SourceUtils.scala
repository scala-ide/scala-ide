package org.scalaide.sbt.util

import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Source
import scala.concurrent.Promise
import scala.concurrent.Future
import play.api.libs.json.JsSuccess
import play.api.libs.json.Reads
import play.api.libs.json.JsError
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import play.api.libs.json.JsString
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

  implicit val ReadsForSeqOfFile: Reads[Seq[File]] = new Reads[Seq[File]] {
    override def reads(v: JsValue) = v match {
      case JsArray(seq) if seq forall (_.isInstanceOf[JsString]) ⇒
        JsSuccess(seq map (e ⇒ new File(e.as[String])))
      case v ⇒
        JsError(s"invalid format: $v")
    }
  }

  implicit val ReadsForSeqOfAttributedFile: Reads[Seq[Attributed[File]]] = new Reads[Seq[Attributed[File]]] {
    override def reads(v: JsValue) = v match {
      case JsArray(seq) if seq forall (_.isInstanceOf[JsString]) ⇒
        ???
      case v ⇒
        JsError(s"invalid format: $v")
    }
  }

  implicit val ReadsForFile: Reads[File] = new Reads[File] {
    override def reads(v: JsValue) = v match {
      case JsString(str) ⇒
        JsSuccess(new File(str))
      case v ⇒
        JsError(s"invalid format: $v")
    }
  }
}
