package org.scalaide.sbt.core.observable

import sbt.client.SbtClient
import sbt.client.TaskResult
import rx.lang.scala.Observable
import sbt.protocol.MinimalBuildStructure
import rx.lang.scala.Observer
import rx.lang.scala.Subscriber
import rx.lang.scala.Subscription
import rx.operators.OperationMulticast
import rx.operators.OperationReplay
import scala.concurrent.Future
import scala.concurrent.Promise
import sbt.client.TaskKey
import sbt.protocol.ScopedKey
import sbt.protocol.TaskSuccess
import sbt.protocol.TaskFailure
import sbt.client.SettingKey
import sbt.client.Interaction
import scala.concurrent.ExecutionContext
import sbt.protocol.Event

/** SbtClient wrapper providing Observable based API
 */
class SbtClientWithObservable(client: SbtClient) {

	// Members declared in sbt.client.SbtClient
	
//  def possibleAutocompletions(partialCommand: String, detailLevel: Int): scala.concurrent.Future[Set[sbt.client.Completion]] = ???
//  def close(): Unit = ???  // TODO: close all observables

  def lookupScopedKey(name: String): Future[Seq[ScopedKey]] = {
    client.lookupScopedKey(name)
  }
  
  def requestExecution(commandOrTask: String, interaction: Option[(Interaction, ExecutionContext)]): Future[Unit] = {
    client.requestExecution(commandOrTask, interaction)
  }


  //------------

  def buildWatcher()(implicit context: ExecutionContext): Observable[MinimalBuildStructure] = {
    val observable = Observable[MinimalBuildStructure] { subscriber =>
      val subscription = client.watchBuild { b: MinimalBuildStructure =>
        subscriber.onNext(b)
      }
      subscriber.add(Subscription {
        subscription.cancel()
      })
    }

    observable
  }
  
  def keyWatcher[T](key: TaskKey[T])(implicit context: ExecutionContext): Observable[(ScopedKey, TaskResult[T])] = {
    val observable = Observable[(ScopedKey, TaskResult[T])] { subscriber =>
      val subscription = client.watch(key) { (scopedKey, result) =>
         subscriber.onNext((scopedKey, result))
      }
      subscriber.add(Subscription {
        subscription.cancel
      })
    }
    observable
  }

  def keyWatcher[T](key: SettingKey[T])(implicit context: ExecutionContext): Observable[(ScopedKey, TaskResult[T])] = {
    val observable = Observable[(ScopedKey, TaskResult[T])] { subscriber =>
      val subscription = client.watch(key) { (scopedKey, result) =>
         subscriber.onNext((scopedKey, result))
      }
      subscriber.add(Subscription {
        subscription.cancel
      })
    }
    observable
  }
  
  def eventWatcher()(implicit context: ExecutionContext): Observable[Event] = {
    val observable = Observable[Event] { subscriber =>
      val subscription = client.handleEvents{ event =>
        subscriber.onNext(event)
      }
      subscriber.add(Subscription {
        subscription.cancel()
      })
    }
    observable
  }
  
}