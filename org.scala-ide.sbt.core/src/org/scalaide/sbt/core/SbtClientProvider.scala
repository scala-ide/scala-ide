package org.scalaide.sbt.core

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.ui.console.MessageConsoleStream
import sbt.client.SbtClient
import com.typesafe.sbtrc.client.AbstractSbtServerLocator
import com.typesafe.sbtrc.client.SimpleConnector
import org.scalaide.sbt.ui.console.ConsoleProvider

object SbtClientProvider extends HasLogger {

  // TODO: this is not thread safe
  private var cache = Map[File, SbtClient]()

  /** Returns the SbtClient for the sbt build located at buildRoot. */
  def sbtClientFor(buildRoot: File): Future[SbtClient] = {
    cache.get(buildRoot) match {
      case Some(client) =>
        Future(client)
      case None =>
        val client = createSbtClientFor(buildRoot)
        // TODO: not thread safe
        client.foreach { c =>
          cache += buildRoot -> c
          val console = ConsoleProvider(buildRoot)
          registerEventHandlers(c, console.newMessageStream())
        }
        client
    }
  }

  /** Create a SbtClient of the sbt build located at buildRoot. */
  private def createSbtClientFor(buildRoot: File): Future[SbtClient] = {
    val connector = new SimpleConnector(buildRoot, new IDEServerLocator)

    val promise = Promise[SbtClient]
    connector.onConnect { promise.success }
    promise.future
  }

  import sbt.protocol._
  private def registerEventHandlers(client: SbtClient, out: MessageConsoleStream): Unit = client handleEvents {
    case LogEvent(LogSuccess(msg))        => out.println(s"[success] $msg")
    case LogEvent(LogMessage(level, msg)) => out.println(s"[$level] $msg")
    case LogEvent(LogStdOut(msg))         => out.println(s"[stdout] $msg")
    case LogEvent(LogStdErr(msg))         => out.println(s"[stderr] $msg")
    case m                                => logger.debug("No event handler for " + m)
  }
}

/** SbtServerLocator returning the bundled sbtLaunch.jar and sbt-server.properties. */
class IDEServerLocator extends AbstractSbtServerLocator {

  override def sbtLaunchJar: java.io.File = SbtRemotePlugin.plugin.SbtLaunchJarLocation

  override def sbtProperties(directory: java.io.File): java.net.URL = SbtRemotePlugin.plugin.sbtProperties

}