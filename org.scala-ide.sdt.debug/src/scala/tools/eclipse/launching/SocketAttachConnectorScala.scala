package scala.tools.eclipse.launching

import java.util.{ List => jList, Map => jMap }
import scala.tools.eclipse.debug.ScalaDebugPlugin
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.debug.core.ILaunch
import org.eclipse.jdi.Bootstrap
import org.eclipse.jdt.launching.IVMConnector
import com.sun.jdi.connect.AttachingConnector
import com.sun.jdi.connect.Connector
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import java.io.IOException
import com.sun.jdi.connect.TransportTimeoutException
import org.eclipse.jdi.TimeoutException

/**
 * Attach connector creating a Scala debug session.
 * Added to the platform through extension point.
 */
object SocketAttachConnectorScala {
  
  /** Configuration key for the hostname */
  val HostnameKey = "hostname"

  /**
   * Return the listening connector provided by Eclipse.
   */
  def attachingConnector(): AttachingConnector = {
    import scala.collection.JavaConverters._
    Bootstrap.virtualMachineManager().attachingConnectors().asScala.find(_.name() == "com.sun.jdi.SocketAttach") match {
      case Some(c) =>
        c
      case None =>
        throw new CoreException(new Status(IStatus.ERROR, ScalaDebugPlugin.id, "Unable to find JDI AttachingConnector"))
    }
  }

}

class SocketAttachConnectorScala extends IVMConnector {
  import SocketAttachConnectorScala._
  import SocketListenConnectorScala._

  // from org.eclipse.jdt.launching.IVMConnector

  override val getArgumentOrder: jList[String] = {
    import scala.collection.JavaConverters._
    List(HostnameKey, PortKey).asJava
  }

  override val getDefaultArguments: jMap[String, Connector.Argument] = {
    val args = attachingConnector.defaultArguments()
    // set a default value for port, otherwise an NPE is thrown. This is required by launcher UI
    import scala.collection.JavaConverters._
    args.asScala.get(PortKey) match {
      case Some(e: Connector.IntegerArgument) =>
        e.setValue(8000)
      case _ =>
    }
    args
  }

  override def getIdentifier(): String = "org.scala-ide.sdt.debug.socketAttachConnector"

  override def getName(): String = "Scala debugger (Socket Attach)"

  override def connect(p: jMap[_, _], monitor: IProgressMonitor, launch: ILaunch) {
    import scala.collection.JavaConverters._
    // convert to a usable type
    val params = p.asInstanceOf[jMap[String, AnyRef]].asScala

    val arguments = attachingConnector.defaultArguments()

    // set the values from the params to the the connector arguments
    arguments.asScala.foreach {
      a =>
        params.get(a._1) match {
          case Some(v: String) =>
            a._2.setValue(v)
          case _ =>
            throw ScalaDebugPlugin.wrapInCoreException("Unable to initialize connection, argument '%s' is not available".format(a._1), null)
        }
    }
    val allowTerminate= launch.getLaunchConfiguration().getAttribute(SocketListenConnectorScala.AllowTerminateKey, false)

    try {
      // connect and create the debug session
      val virtualMachine = attachingConnector.attach(arguments)
      ScalaDebugTarget(virtualMachine, launch, null, true, allowTerminate)
    } catch {
      case e: TimeoutException =>
        throw ScalaDebugPlugin.wrapInCoreException("Unable to connect to the remote VM", e)
      case e: IOException =>
        throw ScalaDebugPlugin.wrapInCoreException("Unable to connect to the remote VM", e)
    }
  }

  // ------------

}