package org.scalaide.debug.internal.launching

import java.io.IOException
import java.util.{ List => JList }
import java.util.{ Map => JMap }

import scala.collection.JavaConverters

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunch
import org.eclipse.debug.core.model.IProcess
import org.eclipse.debug.core.model.IStreamsProxy
import org.eclipse.jdi.Bootstrap
import org.eclipse.jdt.launching.IVMConnector
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.scalaide.debug.internal.model.ScalaDebugTarget

import com.sun.jdi.connect.Connector
import com.sun.jdi.connect.ListeningConnector
import com.sun.jdi.connect.TransportTimeoutException

/**
 * Listen connector creating a Scala debug session.
 * Added to the platform through extension point.
 */
class SocketListenConnectorScala extends IVMConnector with SocketConnectorScala {
  import SocketConnectorScala._

  override def connector(): ListeningConnector = {
    import scala.collection.JavaConverters._
    Bootstrap.virtualMachineManager().listeningConnectors().asScala.find(_.name() == SocketListenName).getOrElse(
      throw ScalaDebugPlugin.wrapInCoreException("Unable to find JDI ListeningConnector", null))
  }

  // from org.eclipse.jdt.launching.IVMConnector

  override val getArgumentOrder: JList[String] = {
    import scala.collection.JavaConverters._
    List(PortKey).asJava
  }

  override val getIdentifier: String = ScalaDebugPlugin.id + ".socketListenConnector"

  override def getName(): String = "Scala debugger (Socket Listen)"

  override def connect(params: JMap[String, String], monitor: IProgressMonitor, launch: ILaunch): Unit = {
    val arguments = generateArguments(params)

    // the port number is needed for the process label. It should be available if we got that far
    import scala.collection.JavaConverters._
    val port = arguments.asScala.get("port") match {
      case Some(e: Connector.IntegerArgument) =>
        e.intValue()
      case _ =>
        0
    }

    // a fake process to display information
    val process = ListenForConnectionProcess(launch, port)

    // Start a job to wait for VM connections
    val job = new ListenForConnectionJob(launch, process, connector(), arguments, extractProjectClasspath(params.asScala.toMap))
    job.setPriority(Job.SHORT)

    job.schedule()

  }

  // ------------

}

object ListenForConnectionProcess {
  /**
   * Create a process instance.
   */
  def apply(launch: ILaunch, port: Int): ListenForConnectionProcess = {
    val process = new ListenForConnectionProcess(launch, port)
    launch.addProcess(process)
    process.fireEvent(DebugEvent.CREATE)
    process
  }
}

/**
 * A fake process which is displayed while waiting for a VM to connect
 */
class ListenForConnectionProcess private (launch: ILaunch, port: Int) extends IProcess {

  // -- from org.eclipse.debug.core.model.IProcess

  def getExitValue(): Int = {
    // should only be used when connection failed
    -1
  }

  def getAttribute(id: String): String = {
    // not supported
    null
  }

  def setAttribute(id: String, value: String): Unit = {
    // nothing to do
  }

  def getStreamsProxy(): IStreamsProxy = {
    // not supported
    null
  }

  def getLaunch(): ILaunch = launch

  def getLabel(): String = label

  // -- from org.eclipse.debug.core.model.ITerminate

  def terminate(): Unit = {
    // not supported
  }

  override def isTerminated(): Boolean = isTerminatedFlag

  def canTerminate(): Boolean = false

  // -- from org.eclipse.core.runtime.IAdaptable

  def getAdapter(adapter: Class[_]): Object = null

  // ----------

  /**
   * Called when no VM connected before the timeout.
   * Set the flag and the label accordingly.
   */
  def failed(message: String): Unit = {
    label = message
    isTerminatedFlag = true
    fireEvent(DebugEvent.TERMINATE)
  }

  /**
   * Called when a VM has connected. The fake process is destroyed
   */
  def done(): Unit = {
    launch.removeProcess(this)
  }

  /**
   * Flag holding the terminated state
   */
  @volatile
  private var isTerminatedFlag = false

  /**
   * Label displayed. Initial value to 'waiting...'. Switched to error message after faiture.
   */
  @volatile
  private var label = "Waiting for connection on port %d...".format(port)

  /**
   * Utility method to fire debug events.
   */
  private def fireEvent(kind: Int): Unit = {
    DebugPlugin.getDefault().fireDebugEventSet(Array(new DebugEvent(this, kind)))
  }
}

/**
 * Job waiting for a VM to connect.
 * If it is successful, it creates a debug target, otherwise an error message is displayed in the debug view.
 */
class ListenForConnectionJob(launch: ILaunch, process: ListenForConnectionProcess, connector: ListeningConnector, arguments: JMap[String, Connector.Argument], projectClasspath: Option[Seq[String]])
    extends Job("Scala debugger remote connection listener") {
  import SocketConnectorScala._

  // -- from org.eclipse.core.runtime.jobs.Job

  override def run(monitor: IProgressMonitor): IStatus = {
    try {
      connector.startListening(arguments)
      val virtualMachine = connector.accept(arguments)
      connector.stopListening(arguments)

      ScalaDebugTarget(virtualMachine, launch, null, true, allowTerminate(launch), projectClasspath)

      connectionSuccesful()
      Status.OK_STATUS
    } catch {
      case e: TransportTimeoutException =>
        connectionFailed("No connection received before timeout expired")
        Status.OK_STATUS
      case e: IOException =>
        connectionFailed("Problem while waiting to receive connection. See log for more details")
        ScalaDebugPlugin.wrapInErrorStatus("Problem while waiting to receive connection", e)
      case e: Exception =>
        connectionFailed("Unexpected problem while waiting to receive connection. See log for more details")
        ScalaDebugPlugin.wrapInErrorStatus("Unexpected problem while waiting to receive connection", e)
    }
  }

  // ------------

  def connectionSuccesful(): Unit = {
    process.done()
  }

  def connectionFailed(message: String): Unit = {
    process.failed(message)
  }

}
