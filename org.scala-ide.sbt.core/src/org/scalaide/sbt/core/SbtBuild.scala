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
import sbt.client.SettingKey
import sbt.client.TaskKey

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
  watchedKeys: Map[String, Future[_]],
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
      if (!promise.trySuccess(sbtClient)) {
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
    buildData = SbtBuildDataContainer(sbtClient, subscription, build, Map(), Nil)
  }

  /** Adds a watcher on the build structure, and returns the first value.
   */
  private def watchBuild(sbtClient: Future[SbtClient]): Future[MinimalBuildStructure] = {
    val promise = Promise[MinimalBuildStructure]

    sbtClient.map { sc =>
      val subscription = sc.watchBuild {
        case b: MinimalBuildStructure =>
          if (!promise.trySuccess(b)) {
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

  private def projectReference(projectName: String): Future[Option[ProjectReference]] = {
    projects().map {
      _.find(_.name == projectName)
    }
  }

  private def sbtClient = withReadLock {
    buildData.sbtClient
  }

  /** Retrive the value (Future) for the given key. If a value has not been cached yet, uses the orElse to
   *  create a value and cache it.
   */
  private def getFromKeyCache[E](keyString: String)(orElse: => Future[E])(implicit mf: Manifest[E]): Future[E] = {
    dataLock.readLock().lock()
    try {
      buildData.watchedKeys.get(keyString) match {
        case Some(e: Future[E]) =>
          e
        case Some(e) =>
          Future.failed(new Exception(s"value for key '$keyString' already cached, but with a different type: $e"))
        case None =>
          dataLock.readLock().unlock()
          dataLock.writeLock().lock()
          try {
            // recheck, in case the 'get' result appears in between the unlock/lock
            buildData.watchedKeys.get(keyString) match {
              case Some(e: Future[E]) =>
                e
              case Some(e) =>
                Future.failed(new Exception(s"value for key '$keyString' already cached, but with a different type: $e"))
              case None =>
                val newValue = orElse
                buildData = buildData.copy(watchedKeys = buildData.watchedKeys + (keyString -> newValue))
                newValue
            }
          } finally {
            dataLock.readLock().lock()
            dataLock.writeLock().unlock()
          }
      }
    } finally {
      dataLock.readLock().unlock()
    }
  }

  /** Store a new value for the given key.
   */
  private def putInKeyCache[E](keyString: String, value: Future[E]) {
    withWriteLock {
      buildData = buildData.copy(watchedKeys = buildData.watchedKeys + (keyString -> value))
    }
  }

  /** Creates the string representing the key
   */
  private def createKeyString(projectName: String, keyName: String, config: Option[String]) =
    s"${projectName}/${config.map(c => s"$c:").mkString}$keyName"

  /** Returns a Future for the value of the given setting key.
   *
   *  Assumes that the values can be serialize, so BuildValue.value.get is always valid.
   */
  def getSettingValue[T](projectName: String, keyName: String, config: Option[String] = None)(implicit mf: Manifest[T]): Future[T] = {

    val keyString = createKeyString(projectName, keyName, config)

    getFromKeyCache(keyString) {
      /* orElse */
      val f = for {
        client <- sbtClient
        scopedKey <- client.lookupScopedKey(keyString)
      } yield {
        val key: SettingKey[T] = SettingKey(scopedKey.head)

        val promise = Promise[T]
        val subscription = client.watch(key) { (scopedKey, result) =>
          result match {
            case TaskSuccess(value) =>
              val v = value.value.get
              if (!promise.trySuccess(v)) {
                putInKeyCache(keyName, Future.successful(v))
              }
            case TaskFailure(msg) =>
              val ex = new Exception(msg)
              if (!promise.tryFailure(ex)) {
                putInKeyCache(keyName, Future.failed(ex))
              }
          }
        }
        addWatchSubscriptionToData(subscription)
        promise.future
      }

      // flatten
      f.flatMap[T](f => f)
    }
  }

  /** Returns a Future for the value of the given task key.
   *
   *  Assumes that the values can be serialize, so BuildValue.value.get is always valid.
   */
  def getTaskValue[T](projectName: String, keyName: String, config: Option[String] = None)(implicit mf: Manifest[T]): Future[T] = {

    val keyString = createKeyString(projectName, keyName, config)

    getFromKeyCache(keyString) {
      /* orElse */
      val f = for {
        client <- sbtClient
        scopedKey <- client.lookupScopedKey(keyString)
      } yield {
        val key: TaskKey[T] = TaskKey(scopedKey.head)

        val promise = Promise[T]
        val subscription = client.watch(key) { (scopedKey, result) =>
          result match {
            case TaskSuccess(value) =>
              val v = value.value.get
              if (!promise.trySuccess(v)) {
                putInKeyCache(keyName, Future.successful(v))
              }
            case TaskFailure(msg) =>
              val ex = new Exception(msg)
              if (!promise.tryFailure(ex)) {
                putInKeyCache(keyName, Future.failed(ex))
              }
          }
        }
        addWatchSubscriptionToData(subscription)
        promise.future
      }

      f.flatMap[T](f => f)
    }
  }

}
