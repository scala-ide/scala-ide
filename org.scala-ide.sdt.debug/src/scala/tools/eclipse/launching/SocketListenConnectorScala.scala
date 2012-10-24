package scala.tools.eclipse.launching

import java.io.IOException

import java.util.{ List => jList, Map => jMap }

import scala.tools.eclipse.debug.ScalaDebugPlugin
import scala.tools.eclipse.debug.model.ScalaDebugTarget

import org.eclipse.core.runtime.CoreException
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

import com.sun.jdi.connect.Connector
import com.sun.jdi.connect.ListeningConnector
import com.sun.jdi.connect.TransportTimeoutException

/**
 * Listen connector create a Scala debug session.
 * Added to the platform through extension point.
 */
object SocketListenConnectorScala {

  /** Configuration key for the 'allow termination of remote VM' option */
  val AllowTerminateKey = "org.eclipse.jdt.launching.ALLOW_TERMINATE"
    
  /** Configuration key for the port number */
  val PortKey = "port"

  /**
   * Return the listening connector provided by Eclipse.
   */
  def listeningConnector(): ListeningConnector = {
    import scala.collection.JavaConverters._
    Bootstrap.virtualMachineManager().listeningConnectors().asScala.find(_.name() == "com.sun.jdi.SocketListen") match {
      case Some(c) =>
        c
      case None =>
        throw new CoreException(new Status(IStatus.ERROR, ScalaDebugPlugin.id, "Unable to find JDI ListeningConnector"))
    }
  }

}

class SocketListenConnectorScala extends IVMConnector {
  import SocketListenConnectorScala._

  // from org.eclipse.jdt.launching.IVMConnector

  override val getArgumentOrder: jList[String] = {
    import scala.collection.JavaConverters._
    List(PortKey).asJava
  }

  override val getDefaultArguments: jMap[String, Connector.Argument] = {
    val args = listeningConnector.defaultArguments()
    // set a default value for port, otherwise an NPE is thrown. This is required by launcher UI
    import scala.collection.JavaConverters._
    args.asScala.get(PortKey) match {
      case Some(e: Connector.IntegerArgument) =>
        e.setValue(8000)
      case _ =>
    }
    args
  }

  override def getIdentifier(): String = "org.scala-ide.sdt.debug.socketListenConnector"

  override def getName(): String = "Scala debugger (Socket Listen)"

  override def connect(p: jMap[_, _], monitor: IProgressMonitor, launch: ILaunch) {
    import scala.collection.JavaConverters._
    // convert to a usable type
    val params = p.asInstanceOf[jMap[String, AnyRef]].asScala

    val arguments = listeningConnector.defaultArguments()

    // set the values from params to the the connector arguments
    arguments.asScala.foreach {
      a =>
        params.get(a._1) match {
          case Some(v: String) =>
            a._2.setValue(v)
          case _ =>
            throw ScalaDebugPlugin.wrapInCoreException("Unable to initialize connection, argument '%s' is not available".format(a._1), null)
        }
    }

    // the port number is needed for the process label. It should be available if we got that far
    val port = arguments.asScala.get("port") match {
      case Some(e: Connector.IntegerArgument) =>
        e.intValue()
      case _ =>
        0
    }
    // a fake process to display information
    val process = ListenForConnectionProcess(launch, port)

    // Start a job to wait for VM connections
    val job = new ListenForConnectionJob(launch, process, listeningConnector(), arguments);
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

  def setAttribute(id: String, value: String) {
    // nothing to do
  }

  def getStreamsProxy(): IStreamsProxy = {
    // not supported
    null
  }

  def getLaunch(): ILaunch = launch

  def getLabel(): String = label

  // -- from org.eclipse.debug.core.model.ITerminate

  def terminate() {
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
  def failed(message: String) {
    label = message
    isTerminatedFlag = true
    fireEvent(DebugEvent.TERMINATE)
  }

  /**
   * Called when a VM has connected. The fake process is destroyed 
   */
  def done() {
    launch.removeProcess(this)
  }

  /**
   * Flag holding the terminated state
   */
  private var isTerminatedFlag = false

  /**
   * Label displayed. Initial value to 'waiting...'. Switched to error message after faiture.
   */
  private var label = "Waiting for connection on port %d...".format(port)

  /**
   * Utility method to fire debug events.
   */
  private def fireEvent(kind: Int) {
    DebugPlugin.getDefault().fireDebugEventSet(Array(new DebugEvent(this, kind)))
  }
}

/**
 * Job waiting for a VM to connect.
 * If it is successful, it creates a debug target, otherwise an error message is displayed in the debug view.
 */
class ListenForConnectionJob(launch: ILaunch, process: ListenForConnectionProcess, connector: ListeningConnector, arguments: jMap[String, Connector.Argument]) extends Job("Scala debugger remote connection listener") {

  // -- from org.eclipse.core.runtime.jobs.Job

  override def run(monitor: IProgressMonitor): IStatus = {
    try {
      connector.startListening(arguments)
      val virtualMachine = connector.accept(arguments)
      connector.stopListening(arguments)

      val allowTerminate = launch.getLaunchConfiguration().getAttribute(SocketListenConnectorScala.AllowTerminateKey, false)

      ScalaDebugTarget(virtualMachine, launch, null, true, allowTerminate)

      Status.OK_STATUS
    } catch {
      case e: TransportTimeoutException =>
        connectionFailed("No connection received before timeout expired")
        Status.OK_STATUS
      case e: IOException =>
        connectionFailed("Problem while waiting to receive connection. See log for more details")
        ScalaDebugPlugin.wrapInErrorStatus("Problem while waiting to receive connection", e)
      case e: Throwable =>
        connectionFailed("Unexpected problem while waiting to receive connection. See log for more details")
        ScalaDebugPlugin.wrapInErrorStatus("Unexpected problem while waiting to receive connection", e)
    }
  }

  // ------------

  def connectionSuccesful() {
    process.done()
  }

  def connectionFailed(message: String) {
    process.failed(message)
  }

}