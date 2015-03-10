package org.scalaide.sbt.core

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.pickling.Unpickler
import scala.util.Try

import org.scalaide.sbt.util.SourceUtils

import KeyProvider.KeyProvider
import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Source
import sbt.client.Interaction
import sbt.client.SbtClient
import sbt.protocol.MinimalBuildStructure
import sbt.protocol.ScopedKey

class RichSbtClient(private[core] val client: SbtClient) {
  import KeyProvider._
  import scala.language.higherKinds

  private type Out[A] = Try[(ScopedKey, A)]

  def watchBuild()(implicit ctx: ExecutionContext): Source[MinimalBuildStructure] = {
    SourceUtils.fromEventStream[MinimalBuildStructure] { subs ⇒
      val c = client.watchBuild { b ⇒
        subs.onNext(b)
      }
      () ⇒ c.cancel()
    }
  }

  def keyValue
      [A : Unpickler, KP[_] : KeyProvider]
      (projectName: String, keyName: String, config: Option[String])
      (implicit sys: ActorSystem): Future[A] = {
    import sys.dispatcher
    client.lookupScopedKey(mkCommand(projectName, keyName, config)) flatMap { keys ⇒
      valueOfKey(implicitly[KeyProvider[KP]].key[A](keys.head))
    }
  }

  def requestExecution(commandOrTask: String, interaction: Option[(Interaction, ExecutionContext)] = None): Future[Long] =
    client.requestExecution(commandOrTask, interaction)

  private def watchKey[A : Unpickler, KP[_] : KeyProvider](key: KP[A])(implicit ctx: ExecutionContext): Source[Out[A]] = {
    SourceUtils.fromEventStream[Out[A]] { subs ⇒
      val cancellation = implicitly[KeyProvider[KP]].watch(key) { (key, res) ⇒
        val elem = res map (key → _)
        subs.onNext(elem)
      }
      () ⇒ cancellation.cancel()
    }
  }

  private def valueOfKey[A : Unpickler, KP[_] : KeyProvider](key: KP[A])(implicit sys: ActorSystem): Future[A] = {
    import sys.dispatcher
    import SourceUtils._
    implicit val materializer = ActorFlowMaterializer()
    watchKey(key).firstFuture.flatMap(elem ⇒ Future.fromTry(elem.map(_._2)))
  }

  private def mkCommand(projectName: String, keyName: String, config: Option[String]): String =
    s"$projectName/${config.map(c ⇒ s"$c:").mkString}$keyName"

}
