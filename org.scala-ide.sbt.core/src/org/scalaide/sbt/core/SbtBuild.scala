package org.scalaide.sbt.core

import java.io.File

import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.pickling.Unpickler
import scala.util.Try

import org.eclipse.core.resources.IProject
import org.eclipse.ui.console.MessageConsole
import org.scalaide.logging.HasLogger
import org.scalaide.sbt.ui.console.ConsoleProvider
import org.scalaide.sbt.util.SbtUtils
import org.scalaide.sbt.util.SourceUtils

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Source
import sbt.client.SbtClient
import sbt.client.SbtConnector
import sbt.client.SettingKey
import sbt.protocol.LogEvent
import sbt.protocol.LogMessage
import sbt.protocol.LogStdErr
import sbt.protocol.LogStdOut
import sbt.protocol.LogSuccess
import sbt.protocol.LogTrace
import sbt.protocol.MinimalBuildStructure
import sbt.protocol.ProjectReference
import sbt.protocol.ScopedKey

object SbtBuild extends AnyRef with HasLogger {

  /** cache from path to SbtBuild instance
   */
  private var builds = immutable.Map[File, SbtBuild]()
  private val buildsLock = new Object

  /**
   * Returns the SbtBuild instance for the given build root path.
   *
   * The passed build root is only considered as a valid sbt project when it
   * contains the `project/build.properties` file.
   */
  def buildFor(buildRoot: File)(implicit system: ActorSystem): Option[SbtBuild] = {
    def checkIfValid: Boolean = {
      val properties = new File(s"${buildRoot.getAbsolutePath}/project/build.properties")
      properties.exists() && properties.isFile()
    }

    if (!checkIfValid) None
    else buildsLock.synchronized {
      builds.get(buildRoot) orElse {
        val build = SbtBuild(buildRoot)
        builds += buildRoot -> build
        Some(build)
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

  private def sbtClientWatcher(connector: SbtConnector)(implicit system: ActorSystem): Future[SbtClient] = {
    val p = Promise[SbtClient]

    def onConnect(client: SbtClient): Unit = {
      implicit val ctx = SbtUtils.RunOnSameThreadContext
      implicit val materializer = ActorFlowMaterializer()

      val src = SbtUtils.protocolEventWatcher[LogEvent](client)
      src.runForeach (_.entry match {
        // TODO remove all cases but the LogMessage ones - we don't really need to watch them
        case LogSuccess(msg) ⇒
          Console.out.println(s"stdout message from sbt-server retrieved: $msg")
        case LogStdOut(msg) ⇒
          Console.out.println(s"stdout message from sbt-server retrieved: $msg")
        case LogStdErr(msg) ⇒
          Console.err.println(s"stderr message from sbt-server retrieved: $msg")
        case LogTrace(exceptionClassName, msg) ⇒
          Console.err.println(s"stderr message of type $exceptionClassName from sbt-server retrieved: $msg")
        case LogMessage(LogMessage.INFO, msg) ⇒
          logger.info(msg)
        case LogMessage(LogMessage.DEBUG, msg) ⇒
          logger.debug(msg)
        case LogMessage(LogMessage.ERROR, msg) ⇒
          logger.error(msg)
        case LogMessage(LogMessage.WARN, msg) ⇒
          logger.warn(msg)
        case _ ⇒
      })
      p.trySuccess(client)
    }

    def onError(reconnecting: Boolean, msg: String): Unit =
      if (reconnecting) logger.debug(s"reconnecting to sbt-server after error: $msg")
      else p.failure(new SbtClientConnectionFailure(msg))

    connector.open(onConnect, onError)(system.dispatcher)

    p.future
  }

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

  import system.dispatcher

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
