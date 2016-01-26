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
import org.eclipse.jdt.launching.SocketUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.debug.internal.EclipseDebugEvent
import org.scalaide.debug.internal.ScalaDebugRunningTest
import org.scalaide.debug.internal.ScalaDebugTestSession

object RemoteConnectorTest extends TestProjectSetup("debug", bundleName = "org.scala-ide.sdt.debug.tests") with ScalaDebugRunningTest {
  import ScalaDebugTestSession._

  final val VmArgsKey = "org.eclipse.jdt.launching.VM_ARGUMENTS"
  final val ConnectKey = "org.eclipse.jdt.launching.CONNECT_MAP"
  val singleThreadMonitor = new Object

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
  def launchInRunMode(launchConfigurationName: String, port: Int): ILaunch = {
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
  def waitForOpenSocket(port: Int): Unit = {
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
  def terminateProcess(launch: ILaunch): Unit = {
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
  def initializeTests(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }
}

trait RemoteConnectorTestPortResource {
  import RemoteConnectorTest._

  @Rule
  def resourceManager = ResourceManager
  private val ResourceManager = new ExternalResource {
    private def savePreferences(): Unit = {
      debugConnectionTimeout = JavaRuntime.getPreferences().getInt(JavaRuntime.PREF_CONNECT_TIMEOUT)
    }

    private def restorePreferences(): Unit = {
      JavaRuntime.getPreferences().setValue(JavaRuntime.PREF_CONNECT_TIMEOUT, debugConnectionTimeout)
    }

    private def cleanDebugSession(): Unit = {
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

    override protected def before(): Unit = {
      port_ = SocketUtil.findFreePort
      savePreferences()
    }

    override protected def after(): Unit = {
      restorePreferences()
      cleanDebugSession()
    }

    override def apply(base: Statement, description: Description): Statement = singleThreadMonitor.synchronized {
      super.apply(base, description)
    }

    private var port_ = -1
    var session: ScalaDebugTestSession = null
    var application: ILaunch = null
    var debugEventListener: IDebugEventSetListener = null
    private var debugConnectionTimeout: Int = -1

    def port = port_
  }
}

/**
 * Test using the Scala remote connectors to debug applications
 */
class RemoteConnectorTest extends RemoteConnectorTestPortResource {
  import RemoteConnectorTest._
  import ScalaDebugTestSession._

  /**
   * Check if it is possible to connect to a running VM.
   */
  @Test(timeout = 5000)
  def attachToRunningVM(): Unit = {
    resourceManager.application = launchInRunMode("HelloWorld listening", resourceManager.port)

    waitForOpenSocket(resourceManager.port)

    resourceManager.session = initDebugSession("Remote attaching", resourceManager.port)

    resourceManager.session.runToLine(TYPENAME_HELLOWORLD + "$", 6)

    resourceManager.session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)
  }

  /**
   * Check if it is possible to connect to a running VM that did not suspend.
   */
  @Ignore("Debugee cannot guarantee to wait for its Debugging")
  @Test(timeout = 10000)
  def attachToNonSuspendedRunningVM(): Unit = {
    resourceManager.application = launchInRunMode("HelloWorld listening not suspended", resourceManager.port)

    waitForOpenSocket(resourceManager.port)

    resourceManager.session = initDebugSession("Remote attaching", resourceManager.port)
    resourceManager.session.launch()
    val bp1 = resourceManager.session.addLineBreakpoint(TYPENAME_SAYHELLOWORLD, 7)
    bp1.setEnabled(true)

    resourceManager.application.getProcesses()(0).getStreamsProxy().write("Scala IDE\n")

    resourceManager.session.waitUntilSuspended()
    resourceManager.session.checkStackFrame(TYPENAME_SAYHELLOWORLD + "$", "main([Ljava/lang/String;)V", 7)
  }

  /**
   * Check if a VM is able to connect to to a waiting debugger.
   */
  /*
   * Test timeout set to 5s. The connection timeout is set to 3s.
   * A passing test should not be more than a couple of seconds.
   */
  @Test(timeout = 5000)
  def listenToAttachingVM(): Unit = {
    // tweak the timeout preference. 3s should be good enough.
    JavaRuntime.getPreferences().setValue(JavaRuntime.PREF_CONNECT_TIMEOUT, 3000)

    resourceManager.session = initDebugSession("Remote listening", resourceManager.port)

    // this command actually launch the debugger
    resourceManager.application = resourceManager.session.runToLine(TYPENAME_HELLOWORLD + "$", 6, () => launchInRunMode("HelloWorld attaching", resourceManager.port))

    assertEquals("The 'fake' process should have been removed after connection", resourceManager.session.debugTarget.getLaunch().getProcesses().length, 0)

    resourceManager.session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)
  }

  /**
   * Check exception throw when trying to connect to a not available VM.
   */
  @Test(timeout = 5000, expected = classOf[CoreException])
  def attachToNothing(): Unit = {
    resourceManager.session = initDebugSession("Remote attaching", resourceManager.port)

    resourceManager.session.runToLine(TYPENAME_HELLOWORLD + "$", 6)
  }

  /**
   * Check that a waiting debugger times out if no VM connects.
   */
  /*
   * Test timeout set to 2s. The connection timeout is set to 10ms.
   * A passing test should not be more than 1second
   */
  @Test(timeout = 5000)
  def listeningToNobody(): Unit = {
    // tweak the timeout preference. 10ms to fail fast
    JavaRuntime.getPreferences().setValue(JavaRuntime.PREF_CONNECT_TIMEOUT, 10)

    val latch = new CountDownLatch(1)

    resourceManager.debugEventListener = addDebugEventListener {
      case EclipseDebugEvent(DebugEvent.TERMINATE, p: ListenForConnectionProcess) if p.getLabel.contains("timeout") =>
        latch.countDown()
    }

    resourceManager.session = initDebugSession("Remote listening", resourceManager.port)

    resourceManager.session.launch()

    latch.await()
  }

  /**
   * Check that disconnect releases a VM, without killing it.
   */
  @Test(timeout = 5000)
  def disconnectReleaseRunningVM(): Unit = {
    resourceManager.application = launchInRunMode("HelloWorld listening", resourceManager.port)

    waitForOpenSocket(resourceManager.port)

    resourceManager.session = initDebugSession("Remote attaching", resourceManager.port)

    assertFalse(getProcess(resourceManager.application).isTerminated())

    resourceManager.session.runToLine(TYPENAME_HELLOWORLD + "$", 6)

    resourceManager.session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)

    val latch = new CountDownLatch(1)

    val CurrentProcess = getProcess(resourceManager.application)

    resourceManager.debugEventListener = addDebugEventListener {
      case EclipseDebugEvent(DebugEvent.TERMINATE, CurrentProcess) =>
        latch.countDown()
    }

    resourceManager.session.disconnect()

    latch.await()

    // check the content of outputStream. Should contain the output
    assertTrue("Invalid outputStream content", CurrentProcess.getStreamsProxy().getOutputStreamMonitor().getContents().contains("Hello, World"))
  }

  /**
   * Check that canTerminate is correctly set
   */
  @Test(timeout = 5000)
  def cannotTerminate(): Unit = {
    resourceManager.application = launchInRunMode("HelloWorld listening", resourceManager.port)

    waitForOpenSocket(resourceManager.port)

    resourceManager.session = initDebugSession("Remote attaching", resourceManager.port)

    assertFalse(getProcess(resourceManager.application).isTerminated())

    resourceManager.session.runToLine(TYPENAME_HELLOWORLD + "$", 6)

    resourceManager.session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)

    assertFalse("canTerminate flag should be false", resourceManager.session.debugTarget.canTerminate)

  }

  /**
   * Check that canTerminate is correctly enabled, and kill the VM when used.
   */
  @Test(timeout = 5000)
  def terminateKillsRunningVM(): Unit = {
    resourceManager.application = launchInRunMode("HelloWorld listening", resourceManager.port)

    waitForOpenSocket(resourceManager.port)

    resourceManager.session = initDebugSession("Remote attaching termination", resourceManager.port)

    assertFalse(getProcess(resourceManager.application).isTerminated())

    resourceManager.session.runToLine(TYPENAME_HELLOWORLD + "$", 6)

    resourceManager.session.checkStackFrame(TYPENAME_HELLOWORLD + "$", "main([Ljava/lang/String;)V", 6)

    assertTrue("canTerminate flag should be true", resourceManager.session.debugTarget.canTerminate)

    val latch = new CountDownLatch(1)

    val CurrentProcess = getProcess(resourceManager.application)

    resourceManager.debugEventListener = addDebugEventListener {
      case EclipseDebugEvent(DebugEvent.TERMINATE, CurrentProcess) =>
        latch.countDown()
    }

    resourceManager.session.terminate()

    latch.await()

    // check the content of outputStream. Should not contain the output
    assertFalse("Invalid outputStream content", CurrentProcess.getStreamsProxy().getOutputStreamMonitor().getContents().contains("Hello, World"))
  }

}
