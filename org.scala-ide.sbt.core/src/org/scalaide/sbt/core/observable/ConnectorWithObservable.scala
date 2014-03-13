package org.scalaide.sbt.core.observable

import sbt.client.SbtConnector
import rx.lang.scala.Observable
import sbt.client.SbtClient
import scala.concurrent.ExecutionContext
import rx.lang.scala.Subscription

/** Connector wrapper providing Observable based API
 */
class ConnectorWithObservable(connector: SbtConnector) {

  def sbtClientWatcher()(implicit context: ExecutionContext): Observable[SbtClient] = {
    val observable = Observable[SbtClient] { subscriber =>
      val subscription = connector.onConnect {
        sbtClient =>
          subscriber.onNext(sbtClient)
      }
      subscriber.add {
        Subscription {
          subscription.cancel()
        }
      }
    }
    observable
  }

}