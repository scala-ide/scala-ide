package org.scalaide.sbt.core

import java.io.File

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.pickling.Unpickler

import org.eclipse.core.resources.IProject
import org.eclipse.ui.console.MessageConsole
import org.scalaide.logging.HasLogger
import org.scalaide.sbt.core.builder.RemoteBuildReporter
import org.scalaide.sbt.ui.console.ConsoleProvider
import org.scalaide.sbt.util.SbtUtils
import org.scalaide.sbt.util.SourceUtils

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import sbt.client.SbtClient
import sbt.client.SbtConnector
import sbt.protocol.CompilationFailure
import sbt.protocol.ExecutionFailure
import sbt.protocol.ExecutionSuccess
import sbt.protocol.LogEvent
import sbt.protocol.LogMessage
import sbt.protocol.LogStdErr
import sbt.protocol.LogStdOut
import sbt.protocol.LogSuccess
import sbt.protocol.LogTrace
import sbt.protocol.MinimalBuildStructure
import sbt.protocol.ProjectReference
import xsbti.Severity

object SbtBuild extends AnyRef with HasLogger {

  /**
   * Caches connections to already running sbt server instances.
   */
  private var builds = Map[File, Future[SbtBuild]]()
  private val buildsLock = new Object

  /**
   * Returns the SbtBuild instance for the given build root path.
   *
   * The passed build root is only considered as a valid sbt project when it
   * contains the `project/build.properties` file.
   */
  def buildFor(buildRoot: File)(implicit system: ActorSystem): Future[SbtBuild] = {
    def checkIfValid: Boolean = {
      val properties = new File(s"${buildRoot.getAbsolutePath}/project/build.properties")
      properties.exists() && properties.isFile()
    }

    if (!checkIfValid)
      Future.failed(new SbtClientConnectionFailure(s"The directory `$buildRoot` doesn't represent a sbt build."))
    else buildsLock.synchronized {
      builds.getOrElse(buildRoot, {
        val build = SbtBuild(buildRoot)
        builds += buildRoot -> build
        build
      })
    }
  }

  def shutdown(implicit system: ExecutionContext): Future[Boolean] = Future.sequence {
    builds.values
  }.flatMap { builds =>
    buildsLock.synchronized {
      builds.foreach { _.shutdown() }
    }
    @tailrec def areClosed: Boolean = if (builds.forall { _.isClosed })
      true
    else
      areClosed

    Future {
      areClosed
    }
  }

  /**
   * Creates and initializes a SbtBuild instance for the given path.
   */
  private def apply(buildRoot: File)(implicit system: ActorSystem): Future[SbtBuild] = {
    import system.dispatcher
    val connector = SbtConnector("scala-ide-sbt-integration", "Scala IDE sbt integration", buildRoot)
    val client = sbtClientWatcher(connector)
    client map (new SbtBuild(buildRoot, _, ConsoleProvider(buildRoot)))
  }

  private def sbtClientWatcher(connector: SbtConnector)(implicit system: ActorSystem): Future[RichSbtClient] = {
    val p = Promise[RichSbtClient]

    def onConnect(client: SbtClient): Unit = {
      implicit val ctx = SbtUtils.RunOnSameThreadContext
      implicit val materializer = ActorMaterializer()

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

/**
 * Represents a connection to a running sbt server instance, which is connected
 * to the build root.
 */
class SbtBuild private (val buildRoot: File, sbtClient: RichSbtClient, console: MessageConsole)(implicit val system: ActorSystem) extends HasLogger {

  import system.dispatcher
  import SourceUtils._
  implicit val materializer = ActorMaterializer()

  private[core] def shutdown(): Unit =
    sbtClient.client.requestSelfDestruct()

  private[core] def isClosed: Boolean =
    sbtClient.client.isClosed

  def watchBuild(): Source[MinimalBuildStructure, NotUsed] =
    sbtClient.watchBuild()

  /**
   * Triggers the compilation of the given project.
   */
  def compile(project: IProject): Future[Long] =
    sbtClient.requestExecution(s"${project.getName}/compile")

  /**
   * Returns the list of projects defined in this build.
   */
  def projects(): Future[Seq[ProjectReference]] =
    sbtClient.watchBuild().firstFuture.map(_.projects.map(_.id))

  /**
   * Returns a Future for the value of the given setting key. An Unpickler is
   * required to deserialize the value from the wire.
   */
  def setting[A : Unpickler](projectName: String, keyName: String, config: Option[String] = None): Future[A] = {
    implicit val kp = KeyProvider.SettingKeyKP(sbtClient.client)
    sbtClient.keyValue(projectName, keyName, config)
  }

  /**
   * Returns a Future for the value of the given task key. An Unpickler is
   * required to deserialize the value from the wire.
   */
  def task[A : Unpickler](projectName: String, keyName: String, config: Option[String] = None): Future[A] = {
    implicit val kp = KeyProvider.TaskKeyKP(sbtClient.client)
    sbtClient.keyValue(projectName, keyName, config)
  }

  def compilationResult(compileId: Long, buildReporter: RemoteBuildReporter) =
    sbtClient.handleEvents().collect {
      case CompilationFailure(id, failure) if compileId == id =>
        buildReporter.createMarker(Option(failure.position), failure.message, failure.severity)
      case ExecutionFailure(id) if id == compileId =>
        buildReporter.createMarker(None, s"compile execution $compileId failed with no CompilationFailure", Severity.Error)
      case ExecutionSuccess(id) if id == compileId =>
        buildReporter.createMarker(None, s"compile execution $compileId succeeded", Severity.Info)
    }.runWith(Sink.head)
}

final class SbtClientConnectionFailure(msg: String) extends RuntimeException(msg)
