package org.scalaide.sbt.core

import java.io.File
import com.typesafe.sbtrc.api.SbtClient
import com.typesafe.sbtrc.client.AbstractSbtServerLocator
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.sbtrc.client.SimpleConnector
import scala.concurrent.Promise

object SbtClientProvider {
  
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
        }
        client
    }
  }
  
  /** Create a SbtClient of the sbt build located at buildRoot.
   */
  private def createSbtClientFor(buildRoot: File): Future[SbtClient] = {
    val connector = new SimpleConnector(buildRoot, new IDEServerLocator)
    val promise = Promise[SbtClient]
    
    connector.onConnect{client => 
      promise.success(client)
    }
    
    promise.future
  }

}

/** SbtServerLocator returning the bundled sbtLaunch.jar and sbt-server.properties.
 */
class IDEServerLocator extends AbstractSbtServerLocator {
  
 def sbtLaunchJar: java.io.File = SbtRemotePlugin.plugin.SbtLaunchJarLocation
 
 def sbtProperties(directory: java.io.File): java.net.URL = SbtRemotePlugin.plugin.sbtProperties
  
}