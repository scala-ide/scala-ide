package org.scalaide.sbt.core

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.pickling.Unpickler
import scala.util.Try

import org.scalaide.sbt.util.SourceUtils

import KeyProvider.KeyProvider
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import sbt.client.Interaction
import sbt.client.SbtClient
import sbt.protocol.MinimalBuildStructure
import sbt.protocol.ScopedKey
import akka.NotUsed
import sbt.protocol.Event

/**
 * Wrapper around [[sbt.client.SbtClient]] to provide more functionality.
 */
class RichSbtClient(private[core] val client: SbtClient) {
  import KeyProvider._
  import scala.language.higherKinds

  private type Out[A] = Try[(ScopedKey, A)]

  /**
   * Retrieves elements that get send to [[sbt.client.SbtClient.watchBuild]] by
   * a Source.
   */
  def watchBuild()(implicit ctx: ExecutionContext): Source[MinimalBuildStructure, NotUsed] = {
    SourceUtils.fromEventStream[MinimalBuildStructure] { subs ⇒
      val c = client.watchBuild { b ⇒
        subs.onNext(b)
      }
      () ⇒ c.cancel()
    }
  }

  /**
   * Returns the value of a given `keyName` relative to a project given by
   * `projectName`. `config` can be specified to further limit the key.
   *
   * Example: `keyValue("testProject", "sourceDirectories", Some("compile"))`
   * will send the request `testProject/compile:sourceDirectories` to sbt.
   */
  def keyValue
      [A : Unpickler, KP[_] : KeyProvider]
      (projectName: String, keyName: String, config: Option[String])
      (implicit sys: ActorSystem): Future[A] = {
    import sys.dispatcher
    client.lookupScopedKey(mkCommand(projectName, keyName, config)) flatMap { keys ⇒
      valueOfKey(implicitly[KeyProvider[KP]].key[A](keys.head))
    }
  }

  /**
   * Forwards to [[sbt.client.SbtClient.requestExecution]].
   */
  def requestExecution(commandOrTask: String, interaction: Option[(Interaction, ExecutionContext)] = None): Future[Long] =
    client.requestExecution(commandOrTask, interaction)

  private def watchKey[A : Unpickler, KP[_] : KeyProvider](key: KP[A])(implicit ctx: ExecutionContext): Source[Out[A], NotUsed] = {
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
    implicit val materializer = ActorMaterializer()
    watchKey(key).firstFuture.flatMap(elem ⇒ Future.fromTry(elem.map(_._2)))
  }

  private def mkCommand(projectName: String, keyName: String, config: Option[String]): String =
    s"$projectName/${config.map(c ⇒ s"$c:").mkString}$keyName"

  def handleEvents()(implicit ec: ExecutionContext): Source[Event, NotUsed] =
    SourceUtils.fromEventStream[Event] { subs =>
      val c = client.handleEvents { e =>
        subs.onNext(e)
      }
      () => c.cancel()
    }
}
