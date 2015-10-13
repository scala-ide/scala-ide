package org.scalaide.debug.internal.launching

import java.util.{ List => JList }
import java.util.{ Map => JMap }
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.debug.core.ILaunch
import org.eclipse.jdi.Bootstrap
import org.eclipse.jdt.launching.IVMConnector
import com.sun.jdi.connect.AttachingConnector
import com.sun.jdi.connect.Connector
import org.scalaide.debug.internal.model.ScalaDebugTarget
import java.io.IOException
import com.sun.jdi.connect.TransportTimeoutException
import org.eclipse.jdi.TimeoutException
import org.eclipse.debug.core.ILaunchConfiguration

/**
 * Attach connector creating a Scala debug session.
 * Added to the platform through extension point.
 */
class SocketAttachConnectorScala extends IVMConnector with SocketConnectorScala {
  import SocketConnectorScala._

  override def connector(): AttachingConnector = {
    import scala.collection.JavaConverters._
    Bootstrap.virtualMachineManager().attachingConnectors().asScala.find(_.name() == SocketAttachName).getOrElse(
      throw ScalaDebugPlugin.wrapInCoreException("Unable to find JDI AttachingConnector", null))
  }

  // from org.eclipse.jdt.launching.IVMConnector

  override val getArgumentOrder: JList[String] = {
    import scala.collection.JavaConverters._
    List(HostnameKey, PortKey).asJava
  }

  override val getIdentifier: String = ScalaDebugPlugin.id + ".socketAttachConnector"

  override def getName(): String = "Scala debugger (Socket Attach)"

  override def connect(params: JMap[String, String], monitor: IProgressMonitor, launch: ILaunch): Unit = {
    import scala.collection.JavaConverters._
    val arguments = generateArguments(params)

    try {
      // connect and create the debug session
      val virtualMachine = connector.attach(arguments)
      val target = ScalaDebugTarget(virtualMachine, launch, null, allowDisconnect = true,
        allowTerminate = allowTerminate(launch), extractProjectClasspath(params.asScala.toMap))
      target.attached() // tell the debug target to initialize
    } catch {
      case e: TimeoutException =>
        throw ScalaDebugPlugin.wrapInCoreException("Unable to connect to the remote VM", e)
      case e: IOException =>
        throw ScalaDebugPlugin.wrapInCoreException("Unable to connect to the remote VM", e)
    }
  }
}
