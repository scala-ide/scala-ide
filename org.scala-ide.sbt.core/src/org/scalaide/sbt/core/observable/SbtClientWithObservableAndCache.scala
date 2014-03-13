package org.scalaide.sbt.core.observable

import sbt.client.TaskKey
import rx.lang.scala.Observable
import sbt.protocol.ScopedKey
import sbt.protocol.TaskResult
import scala.concurrent.Future
import sbt.protocol.MinimalBuildStructure
import scala.concurrent.Promise
import rx.lang.scala.Observer
import ObservableExt.NoValueException
import sbt.protocol.TaskSuccess
import sbt.protocol.TaskFailure
import sbt.client.SettingKey
import sbt.client.SettingKey
import sbt.client.SbtClient
import sbt.protocol.Event
import scala.concurrent.ExecutionContext
import sbt.client.Interaction

/** SbtClient wrapper providing Observable and Future based API
 *  
 *  All Observables, except eventWatcher, will replay the lastest value upon connection.
 */
class SbtClientWithObservableAndCache(client: SbtClient) {

  private val clientExt = new SbtClientWithObservable(client)

  private val keyMap = collection.mutable.HashMap[ScopedKey, Observable[(ScopedKey, TaskResult[_])]]()
  
  def requestExecution(commandOrTask: String, interaction: Option[(Interaction, ExecutionContext)]): Future[Unit] = {
    client.requestExecution(commandOrTask, interaction)
  }

  // using replay triggers the creation of the underlying watch only once, and cache the latest value.
  lazy val buildWatcher: Observable[MinimalBuildStructure] = {
    // TODO: I don't like this import
    import scala.concurrent.ExecutionContext.Implicits.global
    ObservableExt.replay(clientExt.buildWatcher, 1)
  }

  def buildValue: Future[MinimalBuildStructure] = ObservableExt.firstFuture(buildWatcher)
  
  // TODO: to check, but I think this will lead to a call to SbtClient.handleEvents everytime a subscriber is connected to the observable
  lazy val eventWatcher: Observable[Event] = {
    // TODO: I don't like this import
    import scala.concurrent.ExecutionContext.Implicits.global
    clientExt.eventWatcher()
  }

  def keyWatcher[T](key: TaskKey[T])(implicit context: ExecutionContext): Observable[(ScopedKey, TaskResult[T])] = {
    // TODO: more fine grain synchronization (readwritelock ?)
    keyMap synchronized {
      keyMap.get(key.key) match {
        case Some(observable) =>
          // TODO: this doesn't check that the same ScopedKey was no used with a different T
          observable.asInstanceOf[Observable[(ScopedKey, TaskResult[T])]]
        case None =>
          // using replay triggers the creation of the underlying watch only once, and cache the latest value.
          val observable = ObservableExt.replay(clientExt.keyWatcher(key), 1)
          keyMap + (key.key -> observable)
          observable
      }
    }
  }

  /**
   * TODO: This may return the previously computed value. It doesn't request a new value to be computed. Is it a problem?
   */
  def keyValue[T](key: TaskKey[T])(implicit context: ExecutionContext): Future[T] = {
    val promise = Promise[T]
    val subscription = keyWatcher(key)(context) {
      new ScopedKeyResultObserver(promise)
    }
    val future = promise.future

    future.foreach { v => subscription.unsubscribe() }

    future
  }

  def keyWatcher[T](key: SettingKey[T])(implicit context: ExecutionContext): Observable[(ScopedKey, TaskResult[T])] = {
    // TODO: more fine grain synchronization (readwritelock ?)
    keyMap synchronized {
      keyMap.get(key.key) match {
        case Some(observable) =>
          // TODO: this doesn't check that the same ScopedKey was no used with a different T
          observable.asInstanceOf[Observable[(ScopedKey, TaskResult[T])]]
        case None =>
          // using replay triggers the creation of the underlying watch only once, and cache the latest value.
          val observable = ObservableExt.replay(clientExt.keyWatcher(key), 1)
          keyMap + (key.key -> observable)
          observable
      }
    }
  }

  def keyValue[T](key: SettingKey[T])(implicit context: ExecutionContext): Future[T] = {
    val promise = Promise[T]
    val subscription = keyWatcher(key)(context) {
      new ScopedKeyResultObserver(promise)
    }
    val future = promise.future

    future.foreach { v => subscription.unsubscribe() }

    future
  }

  /**
   * Observer fufilling the promise with the first value received.
   * If onNext with a TaskSucces occurs first, the promise is completed successfully, in all other case, the promise is failed.
   */
  class ScopedKeyResultObserver[T](promise: Promise[T]) extends Observer[(ScopedKey, TaskResult[T])]() {
    override def onNext(value: (ScopedKey, TaskResult[T])) {
      value._2 match {
        case TaskSuccess(value) =>
          promise.trySuccess(value.value.get)
        case TaskFailure(msg) =>
          promise.tryFailure(new TaskFailureException(msg))
      }
    }
    override def onError(error: Throwable) {
      promise.tryFailure(error)
    }
    override def onCompleted() {
      promise.tryFailure(new NoValueException())
    }
  }

  class TaskFailureException(msg: String) extends Exception(msg)

  // ---- helper methods ----

  /**
   * Creates the string representing the key
   */
  private def createKeyString(projectName: String, keyName: String, config: Option[String]) =
    s"${projectName}/${config.map(c => s"$c:").mkString}$keyName"

  def getSettingValue[T](projectName: String, keyName: String, config: Option[String] = None)(implicit context: ExecutionContext): Future[T] = {
    clientExt.lookupScopedKey(createKeyString(projectName, keyName, config)).flatMap { keys =>
      keyValue(new SettingKey(keys.head))
    }
  }

  def getTaskValue[T](projectName: String, keyName: String, config: Option[String] = None)(implicit context: ExecutionContext): Future[T] = {
    clientExt.lookupScopedKey(createKeyString(projectName, keyName, config)).flatMap { keys =>
      keyValue(new TaskKey(keys.head))
    }
  }
}