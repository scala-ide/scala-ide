package org.scalaide.sbt.core

import java.io.File

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
import sbt.client.Interaction
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

  /**
   * Caches connections to already running sbt server instances.
   */
  private var builds = Map[File, SbtBuild]()
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
    val client = sbtClientWatcher(connector)
    new SbtBuild(buildRoot, client, ConsoleProvider(buildRoot))
  }

  private def sbtClientWatcher(connector: SbtConnector)(implicit system: ActorSystem): Future[RichSbtClient] = {
    val p = Promise[RichSbtClient]

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
      p.success(new RichSbtClient(client))
    }

    def onError(reconnecting: Boolean, msg: String): Unit =
      if (reconnecting) logger.debug(s"reconnecting to sbt-server after error: $msg")
      else p.failure(new SbtClientConnectionFailure(msg))

    connector.open(onConnect, onError)(system.dispatcher)

    p.future
  }

}

object KeyProvider {
  import sbt.client._
  trait KeyProvider[M[_]] {
    def key[A](key: ScopedKey): M[A]
    def watch[A : Unpickler](a: M[A])(listener: ValueListener[A])(implicit ex: ExecutionContext): Subscription
  }
  implicit def TaskKeyKP(implicit client: SbtClient) = new KeyProvider[TaskKey] {
    def key[A](key: ScopedKey) = TaskKey(key)
    def watch[A : Unpickler](key: TaskKey[A])(listener: ValueListener[A])(implicit ex: ExecutionContext): Subscription =
      client.watch(key)(listener)
  }
  implicit def SettingKeyKP(implicit client: SbtClient) = new KeyProvider[SettingKey] {
    def key[A](key: ScopedKey) = SettingKey(key)
    def watch[A : Unpickler](key: SettingKey[A])(listener: ValueListener[A])(implicit ex: ExecutionContext): Subscription =
      client.watch(key)(listener)
  }

}

class RichSbtClient(private[core] val client: SbtClient) {
  import KeyProvider._

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

final class SbtClientConnectionFailure(msg: String) extends RuntimeException(msg)

class SbtBuild private (val buildRoot: File, sbtClient: Future[RichSbtClient], console: MessageConsole)(implicit val system: ActorSystem) extends HasLogger {

  import system.dispatcher
  import SourceUtils._
  implicit val materializer = ActorFlowMaterializer()

  /**
   * Triggers the compilation of the given project.
   */
  def compile(project: IProject): Future[Long] =
    sbtClient flatMap (_.requestExecution(s"${project.getName}/compile"))

  /**
   * Returns the list of projects defined in this build.
   */
  def projects(): Future[Seq[ProjectReference]] =
    sbtClient flatMap { _.watchBuild().firstFuture.map(_.projects.map(_.id)) }

  /**
   * Returns a Future for the value of the given setting key. An Unpickler is
   * required to deserialize the value from the wire.
   */
  def setting[A : Unpickler](projectName: String, keyName: String, config: Option[String] = None): Future[A] =
    sbtClient flatMap { c ⇒
      implicit val kp = KeyProvider.SettingKeyKP(c.client)
      c.keyValue(projectName, keyName, config)
    }

  /**
   * Returns a Future for the value of the given task key. An Unpickler is
   * required to deserialize the value from the wire.
   */
  def task[A : Unpickler](projectName: String, keyName: String, config: Option[String] = None): Future[A] =
    sbtClient flatMap { c ⇒
      implicit val kp = KeyProvider.TaskKeyKP(c.client)
      c.keyValue(projectName, keyName, config)
    }
}
