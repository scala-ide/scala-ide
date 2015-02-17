package org.scalaide.sbt.core

import java.io.File

import scala.collection.immutable
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
    val connector = SbtConnector("scala-ide-sbt-integration", "Scala IDE sbt integration", buildRoot)
    new SbtBuild(buildRoot, sbtClientWatcher(connector)(system.dispatcher), ConsoleProvider(buildRoot))
  }

  private def sbtClientWatcher(connector: SbtConnector)(implicit ctx: ExecutionContext): Future[SbtClient] = {
    val p = Promise[SbtClient]

    def onConnect(client: SbtClient): Unit = {
      p.trySuccess(client)
    }
    def onError(reconnecting: Boolean, msg: String): Unit = {
      if (reconnecting) ??? // TODO handle reconnecting case
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

class RichSbtClient(val client: SbtClient) {

  def buildWatcher()(implicit ctx: ExecutionContext): Future[MinimalBuildStructure] = {
    val p = Promise[MinimalBuildStructure]
    client.watchBuild(b ⇒ p.trySuccess(b))
    p.future
  }

}

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

final class SbtClientConnectionFailure(msg: String) extends RuntimeException(msg)

class SbtBuild private (val buildRoot: File, sbtClient: Future[SbtClient], console: MessageConsole)(implicit val system: ActorSystem) extends HasLogger {

  import SourceUtils._
  implicit val materializer = ActorFlowMaterializer()
  import system.dispatcher

  private def richSbtClient = sbtClient.map{s ⇒ println("mapping sbtClient"); new RichSbtClient(s)}

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
    f ← richSbtClient
    build ← f.buildWatcher()
  } yield build.projects.map(_.id)(collection.breakOut)

  /**
   * Returns a Future for the value of the given setting key.
   *
   * Assumes that the values can be serialize, so BuildValue.value.get is always valid.
   */
  def getSettingValue[T](projectName: String, keyName: String, config: Option[String] = None)(implicit mf: Manifest[T]): Future[T] = {
//    sbtClientFuture.flatMap(_.getSettingValue[T](projectName, keyName, config))
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
