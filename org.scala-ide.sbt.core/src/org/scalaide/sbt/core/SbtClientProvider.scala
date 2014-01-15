package org.scalaide.sbt.core

import java.io.File
import com.typesafe.sbtrc.api.SbtClient
import com.typesafe.sbtrc.client.AbstractSbtServerLocator
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.sbtrc.client.SimpleConnector
import scala.concurrent.Promise
import scala.tools.eclipse.logging.HasLogger

object SbtClientProvider extends HasLogger {

  
  // TODO: this is not thread safe
  private var cache = Map[File, SbtClient]()

  /** Returns the SbtClient for the sbt build located at buildRoot.
    */
  def sbtClientFor(buildRoot: File): Future[SbtClient] = {
    cache.get(buildRoot) match {
      case Some(client) =>
        Future(client)
      case None =>
        val client= createSbtClientFor(buildRoot)
        // TODO: not thread safe
        client.foreach {c =>
          cache += buildRoot -> c
          registerEventHandlers(c)
        }
        client
    }
  }

  /** Create a SbtClient of the sbt build located at buildRoot.
    */
  private def createSbtClientFor(buildRoot: File): Future[SbtClient] = {
    val connector = new SimpleConnector(buildRoot, new IDEServerLocator)
    val promise = Promise[SbtClient]

    connector.onConnect { client =>
      promise.success(client)
    }

    promise.future
  }

  import com.typesafe.sbtrc.protocol._
  private def registerEventHandlers(client: SbtClient): Unit = client handleEvents {
    case LogEvent(LogSuccess(msg)) => logger.info(s"[success] $msg")
    case LogEvent(LogMessage(level, msg)) => logger.info(s"[$level] $msg")
    case LogEvent(LogStdOut(msg)) => logger.info(s"[stdout] $msg")
    case LogEvent(LogStdErr(msg)) => logger.info(s"[stderr] $msg")
    case m => logger.debug("No handler defined for event " + m)
  }
}

/** SbtServerLocator returning the bundled sbtLaunch.jar and sbt-server.properties.
  */
class IDEServerLocator extends AbstractSbtServerLocator {

  override def sbtLaunchJar: java.io.File = SbtRemotePlugin.plugin.SbtLaunchJarLocation

  override def sbtProperties(directory: java.io.File): java.net.URL = SbtRemotePlugin.plugin.sbtProperties

}