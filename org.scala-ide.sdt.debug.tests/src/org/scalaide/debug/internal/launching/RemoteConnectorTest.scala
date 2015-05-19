package org.scalaide.debug.internal.launching

import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.util.{ Map => JMap }
import java.util.concurrent.CountDownLatch

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.IDebugEventSetListener
import org.eclipse.debug.core.ILaunch
import org.eclipse.debug.core.ILaunchManager
import org.eclipse.debug.core.model.IProcess
import org.eclipse.jdt.launching.JavaRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.debug.internal.EclipseDebugEvent
import org.scalaide.debug.internal.ScalaDebugRunningTest
import org.scalaide.debug.internal.ScalaDebugTestSession
import org.scalaide.debug.internal.ScalaDebugTestSession.addDebugEventListener

object RemoteConnectorTest
  extends TestProjectSetup("debug", bundleName = "org.scala-ide.sdt.debug.tests")
  with ScalaDebugRunningTest with RemoteConnector

trait RemoteConnector {
  self: TestProjectSetup =>
  import ScalaDebugTestSession._

  final val VmArgsKey = "org.eclipse.jdt.launching.VM_ARGUMENTS"
  final val ConnectKey = "org.eclipse.jdt.launching.CONNECT_MAP"

  /**
   * Select a free port by letting the OS pick one and then closing it.
   */
  def freePort(): Int = {
    val socket= new Socket()
    socket.bind(new InetSocketAddress(0)) // bind on all network interface, on a port chosen by the OS
    val port= socket.getLocalPort()
    socket.close()
    port
  }

  /**
   * Create a debug session for the given launch configuration, using the given port.
   */
  def initDebugSession(launchConfigurationName: String, port: Int): ScalaDebugTestSession = {
    val launchConfiguration = DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(file(launchConfigurationName + ".launch"))
    val workingLaunchConfiguration = launchConfiguration.getWorkingCopy()

    // update the port number
    val vmArgs = workingLaunchConfiguration.getAttribute(ConnectKey, null: JMap[String, String]).asInstanceOf[JMap[String, String]]
    vmArgs.put(SocketConnectorScala.PortKey, port.toString)
    workingLaunchConfiguration.setAttribute(VmArgsKey, vmArgs)

    ScalaDebugTestSession(workingLaunchConfiguration)
  }

  /**
   * Launch the given launch configuration, using the given port for the debug connector.
   * Return the launch instance created.
   */
  def launchInRunMode(launchConfigurationName: String, port: Int, delay: Long = 0): ILaunch = {
    // event listener to wait for the creation of the process
    val latch = new CountDownLatch(1)
    val eventListener = addDebugEventListener {
      case EclipseDebugEvent(DebugEvent.CREATE, p: IProcess) =>
        latch.countDown()
    }

    try {
      val launchConfiguration = DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(file(launchConfigurationName + ".launch"))
      val workingLaunchConfiguration = launchConfiguration.getWorkingCopy()

      // update the port number
      val vmArgs = workingLaunchConfiguration
        .getAttribute(VmArgsKey, s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000 -DremoteDebugDelay=$delay")
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
          case _: ConnectException | _: SocketException =>
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

    val eventListener = addDebugEventListener {
      case EclipseDebugEvent(DebugEvent.TERMINATE, `process`) =>
        latch.countDown()
    }

    try {
      if (!process.isTerminated()) {
        process.terminate()
        latch.await()
      }
    } finally {
      DebugPlugin.getDefault().removeDebugEventListener(eventListener)
    }
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
@Ignore("Enable it once #1001464 is fixed")
class RemoteConnectorTest {

  import RemoteConnectorTest._
  import ScalaDebugTestSession._

  private var session: ScalaDebugTestSession = null
  private var application: ILaunch = null
  private var debugEventListener: IDebugEventSetListener = null

  private var debugConnectionTimeout: Int = -1

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
    if (debugEventListener ne null) {
      DebugPlugin.getDefault().removeDebugEventListener(debugEventListener)
      debugEventListener = null
    }
  }

  /**
   * Check if it is possible to connect to a running VM.
   */
  @Test(timeout = 5000)
  def attachToRunningVM() {
    val port = freePort()
    application = launchInRunMode("HelloWorld listening", port)

    waitForOpenSocket(port)

    session = initDebugSession("Remote attaching", port)

    session.runToLine(TYPENAME_HELLOWORLD + "$", 6)

    session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)
  }

  /**
   * Check if it is possible to connect to a running VM that did not suspend.
   */
  @Test(timeout = 5000)
  def attachToNonSuspendedRunningVM() {
    val port = freePort()
    application = launchInRunMode("HelloWorld listening not suspended", port)

    waitForOpenSocket(port)

    session = initDebugSession("Remote attaching", port)
    session.launch()
    val bp1 = session.addLineBreakpoint(TYPENAME_SAYHELLOWORLD, 7)
    bp1.setEnabled(true)

    application.getProcesses()(0).getStreamsProxy().write("Scala IDE\n")

    session.waitUntilSuspended()
    session.checkStackFrame(TYPENAME_SAYHELLOWORLD + "$", "main([Ljava/lang/String;)V", 7)
  }

  /**
   * Check if a VM is able to connect to to a waiting debugger.
   */
  /*
   * Test timeout set to 5s. The connection timeout is set to 3s.
   * A passing test should not be more than a couple of seconds.
   */
  @Test(timeout = 5000)
  def listenToAttachingVM() {
    val port = freePort()
    // tweak the timeout preference. 3s should be good enough.
    JavaRuntime.getPreferences().setValue(JavaRuntime.PREF_CONNECT_TIMEOUT, 3000)

    session = initDebugSession("Remote listening", port)

    // this command actually launch the debugger
    application = session.runToLine(TYPENAME_HELLOWORLD + "$", 6, () => launchInRunMode("HelloWorld attaching", port))

    assertEquals("The 'fake' process should have been removed after connection", session.debugTarget.getLaunch().getProcesses().length, 0)

    session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)
  }

  /**
   * Check exception throw when trying to connect to a not available VM.
   */
  @Test(expected = classOf[CoreException])
  def attachToNothing() {
    val port = freePort()
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
  @Test(timeout = 5000)
  def listeningToNobody() {
    val port = freePort()
    // tweak the timeout preference. 10ms to fail fast
    JavaRuntime.getPreferences().setValue(JavaRuntime.PREF_CONNECT_TIMEOUT, 10)

    val latch = new CountDownLatch(1)

    debugEventListener = addDebugEventListener {
      case EclipseDebugEvent(DebugEvent.TERMINATE, p: ListenForConnectionProcess) if p.getLabel.contains("timeout") =>
        latch.countDown()
    }

    session = initDebugSession("Remote listening", port)

    session.launch()

    latch.await()
  }

  /**
   * Check that disconnect releases a VM, without killing it.
   */
  @Test(timeout = 5000)
  def disconnectReleaseRunningVM() {
    val port = freePort()

    application = launchInRunMode("HelloWorld listening", port)

    waitForOpenSocket(port)

    session = initDebugSession("Remote attaching", port)

    assertFalse(getProcess(application).isTerminated())

    session.runToLine(TYPENAME_HELLOWORLD + "$", 6)

    session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)

    val latch = new CountDownLatch(1)

    val CurrentProcess = getProcess(application)

    debugEventListener = addDebugEventListener {
      case EclipseDebugEvent(DebugEvent.TERMINATE, CurrentProcess) =>
        latch.countDown()
    }

    session.disconnect()

    latch.await()

    // check the content of outputStream. Should contain the output
    assertTrue("Invalid outputStream content", CurrentProcess.getStreamsProxy().getOutputStreamMonitor().getContents().contains("Hello, World"))
  }

  /**
   * Check that canTerminate is correctly set
   */
  @Test(timeout = 2000)
  def cannotTerminate() {
    val port = freePort()

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
    val port = freePort()

    application = launchInRunMode("HelloWorld listening", port)

    waitForOpenSocket(port)

    session = initDebugSession("Remote attaching termination", port)

    assertFalse(getProcess(application).isTerminated())

    session.runToLine(TYPENAME_HELLOWORLD + "$", 6)

    session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)

    assertTrue("canTerminate flag should be true", session.debugTarget.canTerminate)

    val latch = new CountDownLatch(1)

    val CurrentProcess = getProcess(application)

    debugEventListener = addDebugEventListener {
      case EclipseDebugEvent(DebugEvent.TERMINATE, CurrentProcess) =>
        latch.countDown()
    }

    session.terminate()

    latch.await()

    // check the content of outputStream. Should not contain the output
    assertFalse("Invalid outputStream content", CurrentProcess.getStreamsProxy().getOutputStreamMonitor().getContents().contains("Hello, World"))
  }

}
