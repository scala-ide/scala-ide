package scala.tools.eclipse.launching

import org.junit.Test
import org.junit.Assert._
import scala.tools.eclipse.debug.ScalaDebugRunningTest
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.debug.ScalaDebugTestSession
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchManager
import org.junit.After
import org.junit.BeforeClass
import org.eclipse.ui.preferences.ScopedPreferenceStore
import org.eclipse.core.runtime.preferences.InstanceScope
import org.eclipse.debug.core.ILaunch
import org.eclipse.core.runtime.CoreException
import java.util.Date
import java.util.{ Map => jMap }
import org.eclipse.jdt.launching.JavaRuntime
import org.junit.Before
import org.eclipse.debug.core.IDebugEventSetListener
import org.eclipse.debug.core.IDebugEventSetListener
import org.eclipse.debug.core.DebugEvent
import java.util.concurrent.CountDownLatch
import org.eclipse.debug.core.model.IProcess
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Ignore
import java.net.Socket
import java.net.SocketAddress
import java.net.InetSocketAddress
import java.net.ConnectException
import java.net.SocketException

object RemoteConnectorTest extends TestProjectSetup("debug", bundleName = "org.scala-ide.sdt.debug.tests") with ScalaDebugRunningTest {

  val VmArgsKey = "org.eclipse.jdt.launching.VM_ARGUMENTS"
  val ConnectKey = "org.eclipse.jdt.launching.CONNECT_MAP"

  // Iterator to produce a different port number for each test
  val ConnectionPort = Iterator.from(8000)

  /**
   * Create a debug session for the given launch configuration, using the given port.
   */
  def initDebugSession(launchConfigurationName: String, port: Int): ScalaDebugTestSession = {
    val launchConfiguration = DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(file(launchConfigurationName + ".launch"))
    val workingLaunchConfiguration = launchConfiguration.getWorkingCopy()

    // update the port number
    val vmArgs = workingLaunchConfiguration.getAttribute(ConnectKey, null: jMap[_, _]).asInstanceOf[jMap[String, String]]
    vmArgs.put(SocketListenConnectorScala.PortKey, port.toString)
    workingLaunchConfiguration.setAttribute(VmArgsKey, vmArgs)

    new ScalaDebugTestSession(workingLaunchConfiguration)
  }

  /**
   * Launch the given launch configuration, using the given port for the debug connector.
   * Return the launch instance created.
   */
  def launchInRunMode(launchConfigurationName: String, port: Int): ILaunch = {
    // event listener to wait for the creation of the process
    val latch = new CountDownLatch(1)
    val eventListener = new IDebugEventSetListener() {
      def handleDebugEvents(eventSet: Array[DebugEvent]) {
        eventSet foreach { e =>
          e.getSource() match {
            case p: IProcess if (e.getKind() == DebugEvent.CREATE) =>
              latch.countDown()
            case _ =>
          }
        }
      }
    }
    DebugPlugin.getDefault().addDebugEventListener(eventListener)

    try {
      val launchConfiguration = DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(file(launchConfigurationName + ".launch"))
      val workingLaunchConfiguration = launchConfiguration.getWorkingCopy()

      // update the port number
      val vmArgs = workingLaunchConfiguration.getAttribute(VmArgsKey, "")
      workingLaunchConfiguration.setAttribute(VmArgsKey, vmArgs.replace("8000", port.toString))

      val launch = workingLaunchConfiguration.launch(ILaunchManager.RUN_MODE, null)

      // wait for the process to be created
      latch.await()

      launch
    } finally {
      DebugPlugin.getDefault().removeDebugEventListener(eventListener)
    }
  }

  /**
   * Wait for the given port to be available. Fails it doesn't happen in 2 seconds.
   */
  def waitForOpenSocket(port: Int) {
    var socket: Socket = null
    try {
      val startTime = System.currentTimeMillis()
      do {
        assertTrue("Could not detect port %d to be opened in 2 seconds".format(port), System.currentTimeMillis() - startTime < 2000)
        try {
          socket = new Socket()
          socket.connect(new InetSocketAddress("localhost", port))
          Thread.sleep(10)
        } catch {
          case e: ConnectException =>
          case e: SocketException =>
        }
      } while (!socket.isConnected())
    } finally {
      socket.close()
    }
  }

  /**
   * Force the process associated with the given launch to terminate, and wait for associated event.
   */
  def terminateProcess(launch: ILaunch) {
    val process = getProcess(launch)

    val latch = new CountDownLatch(1)

    val eventListener = new IDebugEventSetListener() {
      def handleDebugEvents(eventSet: Array[DebugEvent]) {
        eventSet foreach { e =>
          e.getSource() match {
            case `process` if (e.getKind() == DebugEvent.TERMINATE) =>
              latch.countDown()
            case _ =>
          }
        }
      }
    }
    DebugPlugin.getDefault().addDebugEventListener(eventListener)

    try {
      if (!process.isTerminated()) {
        process.terminate()
        latch.await()
      }
    } finally {
      DebugPlugin.getDefault().removeDebugEventListener(eventListener)
    }

    println("---Output Stream---")
    println(process.getStreamsProxy().getOutputStreamMonitor().getContents())
    println("---Error Stream---")
    println(process.getStreamsProxy().getErrorStreamMonitor().getContents())
    println("---   ---")

  }

  def getProcess(launch: ILaunch): IProcess = {
    launch.getProcesses().head
  }

  @BeforeClass
  def initializeTests() {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }

}

/**
 * Test using the Scala remote connectors to debug applications
 */
class RemoteConnectorTest {

  import RemoteConnectorTest._

  var session: ScalaDebugTestSession = null
  var application: ILaunch = null
  var eventListener: IDebugEventSetListener = null

  var debugConnectionTimeout: Int = -1

  @Before
  def savePreferences() {
    debugConnectionTimeout = JavaRuntime.getPreferences().getInt(JavaRuntime.PREF_CONNECT_TIMEOUT)
  }

  @After
  def restorePreferences() {
    JavaRuntime.getPreferences().setValue(JavaRuntime.PREF_CONNECT_TIMEOUT, debugConnectionTimeout)
  }

  @After
  def cleanDebugSession() {
    if (session ne null) {
      session.terminate()
      session = null
    }
    if (application ne null) {
      terminateProcess(application)
      application = null
    }
    if (eventListener ne null) {
      DebugPlugin.getDefault().removeDebugEventListener(eventListener)
      eventListener = null
    }
  }

  /**
   * Check if it is possible to connect to a running VM.
   */
  @Test
  def attachToRunningVM() {
    val port = ConnectionPort.next
    application = launchInRunMode("HelloWorld listening", port)

    session = initDebugSession("Remote attaching", port)

    session.runToLine(TYPENAME_HELLOWORLD + "$", 6)

    session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)
  }

  /**
   * Check if a VM is able to connect to to a waiting debugger.
   */
  /*
   * Test timeout set to 5s. The connection timeout is set to 3s.
   * A passing test should not be more than a couple of seconds.
   */
  @Test(timeout = 25000)
  def listenToAttachingVM() {
    val port = ConnectionPort.next
    // tweak the timeout preference. 3s should be good enough.
    JavaRuntime.getPreferences().setValue(JavaRuntime.PREF_CONNECT_TIMEOUT, 3000)

    session = initDebugSession("Remote listening", port)

    // this command actually launch the debugger
    application = session.runToLine(TYPENAME_HELLOWORLD + "$", 6, launchInRunMode("HelloWorld attaching", port))

    session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)
  }

  /**
   * Check exception throw when trying to connect to a not available VM.
   */
  @Test(expected = classOf[CoreException])
  def attachToNothing() {
    val port = ConnectionPort.next
    session = initDebugSession("Remote attaching", port)

    session.runToLine(TYPENAME_HELLOWORLD + "$", 6)
  }

  /**
   * Check that a waiting debugger times out if no VM connects.
   */
  /*
   * Test timeout set to 2s. The connection timeout is set to 10ms.
   * A passing test should not be more than 1second
   */
  @Test(timeout = 2000)
  def listeningToNobody() {
    val port = ConnectionPort.next
    // tweak the timeout preference. 10ms to fail fast
    JavaRuntime.getPreferences().setValue(JavaRuntime.PREF_CONNECT_TIMEOUT, 10)

    val latch = new CountDownLatch(1)

    eventListener = new IDebugEventSetListener() {
      def handleDebugEvents(eventSet: Array[DebugEvent]) {
        eventSet foreach { e =>
          e.getSource() match {
            case p: ListenForConnectionProcess if (e.getKind() == DebugEvent.TERMINATE && p.getLabel.contains("timeout")) =>
              latch.countDown()
            case _ =>
          }
        }
      }
    }
    DebugPlugin.getDefault().addDebugEventListener(eventListener)

    session = initDebugSession("Remote listening", port)

    session.launch

    latch.await()
  }

  /**
   * Check that disconnect releases a VM, without killing it.
   */
  @Test(timeout = 5000)
  def disconnectReleaseRunningVM() {
    val port = ConnectionPort.next

    application = launchInRunMode("HelloWorld listening", port)

    waitForOpenSocket(port)

    session = initDebugSession("Remote attaching", port)

    assertFalse(getProcess(application).isTerminated())

    session.runToLine(TYPENAME_HELLOWORLD + "$", 6)

    session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)

    val latch = new CountDownLatch(1)

    val CurrentProcess = getProcess(application)

    eventListener = new IDebugEventSetListener() {
      def handleDebugEvents(eventSet: Array[DebugEvent]) {
        eventSet foreach { e =>
          e.getSource() match {
            case CurrentProcess if (e.getKind() == DebugEvent.TERMINATE) =>
              latch.countDown()
            case _ =>
          }
        }
      }
    }
    DebugPlugin.getDefault().addDebugEventListener(eventListener)

    session.disconnect

    latch.await()

    // check the content of outputStream. Should contain the output
    assertTrue("Invalid outputStream content", getProcess(application).getStreamsProxy().getOutputStreamMonitor().getContents().contains("Hello, World"))
  }

  /**
   * Check that canTerminate is correctly set
   */
  @Test
  def cannotTerminate() {
    val port = ConnectionPort.next

    application = launchInRunMode("HelloWorld listening", port)

    waitForOpenSocket(port)

    session = initDebugSession("Remote attaching", port)

    assertFalse(getProcess(application).isTerminated())

    session.runToLine(TYPENAME_HELLOWORLD + "$", 6)

    session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)
    
    assertFalse("canTerminate flag should be false", session.debugTarget.canTerminate)

  }
  
  /**
   * Check that canTerminate is correctly enabled, and kill the VM when used.
   */
  @Test(timeout = 5000)
  def terminateKillsRunningVM() {
    val port = ConnectionPort.next

    application = launchInRunMode("HelloWorld listening", port)

    waitForOpenSocket(port)

    session = initDebugSession("Remote attaching termination", port)

    assertFalse(getProcess(application).isTerminated())

    session.runToLine(TYPENAME_HELLOWORLD + "$", 6)

    session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)

    assertTrue("canTerminate flag should be true", session.debugTarget.canTerminate)
    
    val latch = new CountDownLatch(1)

    val CurrentProcess = getProcess(application)

    eventListener = new IDebugEventSetListener() {
      def handleDebugEvents(eventSet: Array[DebugEvent]) {
        eventSet foreach { e =>
          e.getSource() match {
            case CurrentProcess if (e.getKind() == DebugEvent.TERMINATE) =>
              latch.countDown()
            case _ =>
          }
        }
      }
    }
    DebugPlugin.getDefault().addDebugEventListener(eventListener)

    session.terminate

    latch.await()

    // check the content of outputStream. Should not contain the output
    assertFalse("Invalid outputStream content", getProcess(application).getStreamsProxy().getOutputStreamMonitor().getContents().contains("Hello, World"))
  }


}