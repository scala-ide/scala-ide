package org.scalaide.sbt.core

import java.io.File

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.eclipse.core.resources.IProject
import org.eclipse.ui.console.MessageConsole
import org.scalaide.logging.HasLogger
import org.scalaide.sbt.core.observable.ConnectorWithObservable
import org.scalaide.sbt.core.observable.ObservableExt
import org.scalaide.sbt.core.observable.SbtClientWithObservableAndCache
import org.scalaide.sbt.ui.console.ConsoleProvider

import com.typesafe.sbtrc.client.AbstractSbtServerLocator
import com.typesafe.sbtrc.client.SimpleConnector

import rx.lang.scala.Observable
import sbt.client.SbtClient
import sbt.protocol.LogEvent
import sbt.protocol.LogMessage
import sbt.protocol.LogStdErr
import sbt.protocol.LogStdOut
import sbt.protocol.LogSuccess
import sbt.protocol.ProjectReference

object SbtBuild {

  /** cache from path to SbtBuild instance
   */
  private var builds = immutable.Map[File, SbtBuild]()
  private val buildsLock = new Object

  /** Returns the SbtBuild instance for the given path
   */
  def buildFor(buildRoot: File): SbtBuild = {
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
  private def apply(buildRoot: File): SbtBuild = {
    val connector = new ConnectorWithObservable(new SimpleConnector(buildRoot, new IDEServerLocator))
    new SbtBuild(buildRoot, connector.sbtClientWatcher(), ConsoleProvider(buildRoot))
  }

  /** SbtServerLocator returning the bundled sbtLaunch.jar and sbt-server.properties. */
  private class IDEServerLocator extends AbstractSbtServerLocator {

    override def sbtLaunchJar: java.io.File = SbtRemotePlugin.plugin.SbtLaunchJarLocation

    override def sbtProperties(directory: java.io.File): java.net.URL = SbtRemotePlugin.plugin.sbtProperties

  }

}

/** Wrapper for the connection to the sbt-server for a sbt build.
 */
class SbtBuild private (val buildRoot: File, sbtClient_ : Observable[SbtClient], console: MessageConsole) extends HasLogger {

  val sbtClientObservable = ObservableExt.replay(sbtClient_.map{s => println("mapping sbtClient"); new SbtClientWithObservableAndCache(s)}, 1)

  def sbtClientFuture = ObservableExt.firstFuture(sbtClientObservable)
  
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

  /** Triggers the compilation of the given project.
   */
  def compile(project: IProject) {
    for {
      sbtClient <- sbtClientFuture
    } {
      sbtClient.requestExecution(s"${project.getName}/compile", None)
    }
  }

  /** Returns the list of projects defined in this build.
   */
  def projects(): Future[immutable.Seq[ProjectReference]] = {
    for {
      sbtClient <- sbtClientFuture
      build <- sbtClient.buildValue
    } yield {
      build.projects.to[immutable.Seq]
    }
  }

  /** Returns a Future for the value of the given setting key.
   *
   *  Assumes that the values can be serialize, so BuildValue.value.get is always valid.
   */
  def getSettingValue[T](projectName: String, keyName: String, config: Option[String] = None)(implicit mf: Manifest[T]): Future[T] = {
    sbtClientFuture.flatMap(_.getSettingValue[T](projectName, keyName, config))
  }

  /** Returns a Future for the value of the given task key.
   *
   *  Assumes that the values can be serialize, so BuildValue.value.get is always valid.
   */
  def getTaskValue[T](projectName: String, keyName: String, config: Option[String] = None)(implicit mf: Manifest[T]): Future[T] = {
    sbtClientFuture.flatMap(_.getTaskValue(projectName, keyName, config))
  }

}
