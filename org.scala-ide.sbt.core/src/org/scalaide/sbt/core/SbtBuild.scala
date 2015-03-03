package org.scalaide.sbt.core

import java.io.File
import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import org.eclipse.core.resources.IProject
import org.eclipse.ui.console.MessageConsole
import org.scalaide.logging.HasLogger
import org.scalaide.sbt.ui.console.ConsoleProvider
import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Source
import sbt.client.SbtClient
import sbt.client.SbtConnector
import sbt.protocol.MinimalBuildStructure
import sbt.protocol.ProjectReference
import sbt.client.SettingKey
import sbt.client.SettingKey
import sbt.protocol.ScopedKey
import sbt.protocol.TaskResult
import sbt.protocol.TaskSuccess
import scala.util.Try
import org.reactivestreams.Publisher
import org.reactivestreams.Subscription
import org.reactivestreams.Subscriber
import scala.pickling.Unpickler
import org.scalaide.sbt.util.SourceUtils

object SbtBuild {

  /** cache from path to SbtBuild instance
   */
  private var builds = immutable.Map[File, SbtBuild]()
  private val buildsLock = new Object

  /** Returns the SbtBuild instance for the given path
   */
  def buildFor(buildRoot: File)(implicit system: ActorSystem): SbtBuild = {
    buildsLock.synchronized {
      builds.get(buildRoot) match {
        case Some(build) =>
          build
        case None =>
          val build = SbtBuild(buildRoot)
          builds += buildRoot -> build
          build
      }
    }
  }

  /** Create and initialize a SbtBuild instance for the given path.
   */
  private def apply(buildRoot: File)(implicit system: ActorSystem): SbtBuild = {
    import system.dispatcher
    val connector = SbtConnector("scala-ide-sbt-integration", "Scala IDE sbt integration", buildRoot)
    val client = sbtClientWatcher(connector).map(new RichSbtClient(_))
    new SbtBuild(buildRoot, client, ConsoleProvider(buildRoot))
  }

  private def sbtClientWatcher(connector: SbtConnector)(implicit ctx: ExecutionContext): Future[SbtClient] = {
    val p = Promise[SbtClient]

    def onConnect(client: SbtClient): Unit = {
      p.trySuccess(client)
    }
    def onError(reconnecting: Boolean, msg: String): Unit = {
      if (reconnecting) println(s"reconnecting after error: $msg")
      else p.failure(new SbtClientConnectionFailure(msg))
    }
    connector.open(onConnect, onError)

    p.future
  }

  /*
  def sbtClientWatcher(connector: SbtConnector)(implicit ctx: ExecutionContext): Source[SbtClient] = {
    val p = new Publisher[SbtClient] {
      override def subscribe(s: Subscriber[_ >: SbtClient]): Unit = {
        def onConnect(client: SbtClient): Unit = {
          s.onNext(client)
          s.onComplete()
        }
        def onError(reconnecting: Boolean, msg: String): Unit = {
          if (reconnecting) ??? else s.onError(new SbtClientConnectionFailure(msg))
        }
        connector.open(onConnect, onError)
      }
    }
    Source(p)
  }
  */
}

class RichSbtClient(private val client: SbtClient) {

  private val cachedKeys = mutable.HashMap[ScopedKey, Source[Try[(ScopedKey, Any)]]]()

  def buildWatcher()(implicit ctx: ExecutionContext): Future[MinimalBuildStructure] = {
    val p = Promise[MinimalBuildStructure]
    client.watchBuild(b ⇒ p.trySuccess(b))
    p.future
  }

  def settingValue[A : Unpickler](projectName: String, keyName: String, config: Option[String])(implicit sys: ActorSystem): Future[A] = {
    import sys.dispatcher
    client.lookupScopedKey(mkCommand(projectName, keyName, config)) flatMap { keys ⇒
      valueOfKey(SettingKey(keys.head))
    }
  }

  private type Out[A] = Try[(ScopedKey, A)]

  private def watchKey[A : Unpickler](key: SettingKey[A])(implicit ctx: ExecutionContext): Source[Out[A]] = {
    SourceUtils.fromEventStream[Out[A]] { subs ⇒
      val cancellation = client.watch(key) { (key, res) ⇒
        val elem = res map (key → _)
        subs.onNext(elem)
      }
      () ⇒ cancellation.cancel()
    }
  }

  private def keyWatcher[A : Unpickler](key: SettingKey[A])(implicit ctx: ExecutionContext): Source[Out[A]] = {
    cachedKeys synchronized {
      val res = cachedKeys get key.key match {
        case Some(f) ⇒
          f
        case _ ⇒
          val f = watchKey(key)
          cachedKeys update (key.key, f)
          f
      }
      res.asInstanceOf[Source[Try[(ScopedKey, A)]]]
    }
  }

  private def valueOfKey[A : Unpickler](key: SettingKey[A])(implicit sys: ActorSystem): Future[A] = {
    import sys.dispatcher
    implicit val materializer = ActorFlowMaterializer()
    val p = Promise[A]
    watchKey(key).take(1).runForeach(res ⇒ p.tryComplete(res.map(_._2)))
    p.future
  }

  private def mkCommand(projectName: String, keyName: String, config: Option[String]): String =
    s"$projectName/${config.map(c ⇒ s"$c:").mkString}$keyName"

}

final class SbtClientConnectionFailure(msg: String) extends RuntimeException(msg)

class SbtBuild private (val buildRoot: File, sbtClient: Future[RichSbtClient], console: MessageConsole)(implicit val system: ActorSystem) extends HasLogger {

  implicit val materializer = ActorFlowMaterializer()
  import system.dispatcher

/*
  sbtClientObservable.subscribe{ sbtClient =>
    val out = console.newMessageStream()
    sbtClient.eventWatcher.subscribe {
      _ match {
        case LogEvent(LogSuccess(msg)) => out.println(s"[success] $msg")
        case LogEvent(LogMessage(level, msg)) => out.println(s"[$level] $msg")
        case LogEvent(LogStdOut(msg)) => out.println(s"[stdout] $msg")
        case LogEvent(LogStdErr(msg)) => out.println(s"[stderr] $msg")
        case m => logger.debug("No event handler for " + m)
      }
    }
  }
*/

  /**
   * Triggers the compilation of the given project.
   */
  def compile(project: IProject) {
    /*
    for {
      sbtClient <- sbtClientFuture
    } {
      sbtClient.requestExecution(s"${project.getName}/compile", None)
    }
    */
    ???
  }

  /**
   * Returns the list of projects defined in this build.
   */
  def projects(): Future[immutable.Seq[ProjectReference]] = for {
    f ← sbtClient
    build ← f.buildWatcher()
  } yield build.projects.map(_.id)(collection.breakOut)

  def setting[A : Unpickler](projectName: String, keyName: String, config: Option[String] = None): Future[A] =
    sbtClient.flatMap(_.settingValue(projectName, keyName, config))

  /**
   * Returns a Future for the value of the given setting key.
   *
   * Assumes that the values can be serialize, so BuildValue.value.get is always valid.
   */
  @deprecated("use setting instead")
  def getSettingValue[T](projectName: String, keyName: String, config: Option[String] = None)(implicit mf: Manifest[T]): Future[T] = {
    ???
  }

  /**
   * Returns a Future for the value of the given task key.
   *
   * Assumes that the values can be serialize, so BuildValue.value.get is always valid.
   */
  def getTaskValue[T](projectName: String, keyName: String, config: Option[String] = None)(implicit mf: Manifest[T]): Future[T] = {
//    sbtClientFuture.flatMap(_.getTaskValue(projectName, keyName, config))
    ???
  }

}
