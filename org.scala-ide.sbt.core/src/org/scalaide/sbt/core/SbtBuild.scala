package org.scalaide.sbt.core

import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import org.scalaide.logging.HasLogger

import org.eclipse.core.resources.IProject
import org.eclipse.ui.console.MessageConsole
import org.scalaide.sbt.ui.console.ConsoleProvider

import com.typesafe.sbtrc.client.AbstractSbtServerLocator
import com.typesafe.sbtrc.client.SimpleConnector

import sbt.client.SbtClient
import sbt.client.Subscription
import sbt.protocol._

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
    val build = new SbtBuild(buildRoot, ConsoleProvider(buildRoot))
    build.connect()
    build
  }

  /** SbtServerLocator returning the bundled sbtLaunch.jar and sbt-server.properties. */
  private class IDEServerLocator extends AbstractSbtServerLocator {

    override def sbtLaunchJar: java.io.File = SbtRemotePlugin.plugin.SbtLaunchJarLocation

    override def sbtProperties(directory: java.io.File): java.net.URL = SbtRemotePlugin.plugin.sbtProperties

  }

}

/** Internal data structure containing info around SbtClient.
 */
case class SbtBuildDataContainer(
    sbtClient: Future[SbtClient],
    sbtClientSubscription: Subscription,
    build: Future[MinimalBuildStructure],
    subscriptions: List[Subscription])

/** Wrapper for the connection to the sbt-server for a sbt build.
 */
class SbtBuild private (buildRoot: File, console: MessageConsole) extends HasLogger {

  import SbtBuild._

  /** Internal data.
   */
  @volatile private var buildData: SbtBuildDataContainer = null
  private val dataLock = new ReentrantReadWriteLock

  /** To be called synchronously during the instance initialization.
   */
  private def connect() {
    val connector = new SimpleConnector(buildRoot, new IDEServerLocator)

    val promise = Promise[SbtClient]
    // the function passed to onConnect is called everytime the connection with
    // sbt-server is (re)established.
    val subscription = connector.onConnect { sbtClient =>
      if (!promise.isCompleted) {
        promise.success(sbtClient)
      } else {
        withWriteLock {
          newSbtClient(sbtClient)
        }
      }
    }
    firstSbtClient(promise.future, subscription)
  }

  /** Initializes the data instance for the initial connection with the sbt-server
   */
  private def firstSbtClient(sbtClient: Future[SbtClient], subscription: Subscription) {
    withWriteLock {
      initData(sbtClient, subscription)
    }
  }

  /** Refreshes the data instance for the subsequente connections with the sbt-server
   */
  private def newSbtClient(sbtClient: SbtClient) {
    withWriteLock {
      initData(Future(sbtClient), buildData.sbtClientSubscription)
    }
  }

  /** Creates or re-create the internal data for the new sbtClient.
   *  Creates new watchers for elements watched on the previous sbtClient.
   *  Connects the events to the console.
   *  
   *  To be called inside a writeLock
   */
  private def initData(sbtClient: Future[SbtClient], subscription: Subscription) {
    val build = watchBuild(sbtClient)
    connectConsole(sbtClient)
    buildData = SbtBuildDataContainer(sbtClient, subscription, build, Nil)
  }

  /** Adds a watcher on the build structure, and returns the first value.
   */
  private def watchBuild(sbtClient: Future[SbtClient]): Future[MinimalBuildStructure] = {
    val promise = Promise[MinimalBuildStructure]

    sbtClient.map { sc =>
      val subscription = sc.watchBuild {
        case b: MinimalBuildStructure =>
          if (!promise.isCompleted) {
            promise.success(b)
          } else {
            withWriteLock {
              buildData = buildData.copy(build = Future(b))
            }
          }
      }
      addWatchSubscriptionToData(subscription)
    }

    promise.future
  }

  /** Store the subscription in the data instance.
   */
  private def addWatchSubscriptionToData(subscription: Subscription) {
    withWriteLock {
      buildData = buildData.copy(subscriptions = subscription :: buildData.subscriptions)
    }
  }

  /** Adds an event listener
   */
  private def connectConsole(sbtClient: Future[SbtClient]) {
    val out = console.newMessageStream()
    sbtClient.map {
      _ handleEvents {
        case LogEvent(LogSuccess(msg))        => out.println(s"[success] $msg")
        case LogEvent(LogMessage(level, msg)) => out.println(s"[$level] $msg")
        case LogEvent(LogStdOut(msg))         => out.println(s"[stdout] $msg")
        case LogEvent(LogStdErr(msg))         => out.println(s"[stderr] $msg")
        case m                                => logger.debug("No event handler for " + m)
      }
    }
  }

  private def withWriteLock[E](f: => E): E = {
      dataLock.writeLock.lock()
      try {
        f
      } finally {
        dataLock.writeLock.unlock()
      }
  }
  
  private def withReadLock[E](f: => E): E = {
      dataLock.readLock.lock()
      try {
        f
      } finally {
        dataLock.readLock.unlock()
      }
  }
  
  /** Triggers the compilation of the given project.
   */
  def compile(project: IProject) {
    withReadLock {
      buildData.sbtClient.foreach(_.requestExecution(s"${project.getName}/compile", None))
    }
  }

  /** Returns the list of projects defined in this build.
   */
  def projects(): Future[immutable.Seq[ProjectReference]] = {
    withReadLock {
      buildData.build.map(_.projects.to[immutable.Seq])
    }
  }

}
