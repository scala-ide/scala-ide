package org.scalaide.debug.internal.launching

import com.sun.jdi.connect.Connector
import org.eclipse.jdt.launching.IVMConnector
import java.util.{Map => JMap}
import org.eclipse.debug.core.ILaunch
import org.scalaide.debug.internal.ScalaDebugPlugin
import com.sun.jdi.connect.Connector.Argument

object SocketConnectorScala {
    /** Configuration key for the 'allow termination of remote VM' option */
  final val AllowTerminateKey = "org.eclipse.jdt.launching.ALLOW_TERMINATE"

  /** Configuration key for the port number */
  final val PortKey = "port"

  /** Configuration key for the hostname */
  final val HostnameKey = "hostname"

  final val DefaultPort = 8000

  /* magic names */
  final val SocketListenName = "com.sun.jdi.SocketListen"
  final val SocketAttachName = "com.sun.jdi.SocketAttach"


  /**
   * Returns <code>true</code> if AllowTerminate was set to true in the launch configuration, <code>false</code> otherwise.
   */
  def allowTerminate(launch: ILaunch):Boolean =
    launch.getLaunchConfiguration().getAttribute(AllowTerminateKey, false)

}

/**
 * Trait providing common methods for Scala VM connectors.
 */
trait SocketConnectorScala extends IVMConnector {
  import SocketConnectorScala._

  /**
   * Return the JDI connector be used by this VM connector.
   */
  def connector(): Connector

  /**
   * Return the default arguments for this connector.
   */
  override val getDefaultArguments: JMap[String, Connector.Argument] = {
    val args = connector.defaultArguments()
    // set a default value for port, otherwise an NPE is thrown. This is required by launcher UI
    import scala.collection.JavaConverters._
    args.asScala.get(PortKey) match {
      case Some(e: Connector.IntegerArgument) =>
        e.setValue(DefaultPort)
      case _ =>
    }
    args
  }

  /**
   * Create an argument map containing the values provided in the params map.
   */
  def generateArguments(params: JMap[String, String]): JMap[String, Argument] = {
    import scala.collection.JavaConverters._
    // convert to a usable type
    val p = params.asScala

    val arguments= connector.defaultArguments()

    // set the values from the params to the the connector arguments
    arguments.asScala.foreach {
      case (key, value) =>
        p.get(key) match {
          case Some(v: String) =>
            value.setValue(v)
          case _ =>
            throw ScalaDebugPlugin.wrapInCoreException("Unable to initialize connection, argument '%s' is not available".format(key), null)
        }
    }

    arguments
  }

  def extractProjectClasspath(params: Map[String, String]): Option[Seq[String]] = {
    import ScalaRemoteApplicationLaunchConfigurationDelegate._
    params.get(DebugeeProjectClasspath).map { _.split(DebugeeProjectClasspathSeparator).toSeq }
  }
}
