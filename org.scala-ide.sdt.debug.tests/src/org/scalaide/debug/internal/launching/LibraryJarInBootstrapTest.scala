package org.scalaide.debug.internal.launching

import org.scalaide.debug.internal.ScalaDebugPlugin
import org.junit.Before
import org.scalaide.core.testsetup.TestProjectSetup
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Test
import org.junit.Assert._
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchManager
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path
import scala.io.Source
import scala.io.Codec
import org.eclipse.core.resources.IResource
import org.eclipse.debug.core.ILaunchesListener2
import org.eclipse.debug.core.ILaunch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object LibraryJarInBootstrapTest extends TestProjectSetup("launching-1000919", bundleName = "org.scala-ide.sdt.debug.tests")

class LibraryJarInBootstrapTest {

  import LibraryJarInBootstrapTest._

  @Before
  def initializeTests() {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }

  /**
   * Ticket 1000919.
   * Before the fix, if order of the Scala library container and the JRE container was 'wrong', the Scala library was not added
   * to the boot classpath for test execution (nor the classpath) and the launch would fail.
   */
  @Test
  def checkTestIsCorrectlyExecutedWhenLibraryJarAfterJRE() {

    val launchConfigurationName = "t1000919.ScalaTest"

    val latch = new CountDownLatch(1)

    DebugPlugin.getDefault().getLaunchManager.addLaunchListener(onLaunchTerminates(launchConfigurationName, latch.countDown))

    // launch the saved launch configuration
    val launchConfiguration = DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(file(launchConfigurationName + ".launch"))
    val launch = launchConfiguration.launch(ILaunchManager.RUN_MODE, null)

    // wait for the launch to terminate
    latch.await(10, TimeUnit.SECONDS)
    assertTrue("launch did not terminate in 10s", launch.isTerminated)

    // check the result
    // refresh the project to be able to see the new file
    project.underlying.refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor)
    val resultFile = file("t1000919.result")
    assertTrue("No result file, the launched test likely failed to run", resultFile.exists)

    // check the content
    val source = Source.fromInputStream(resultFile.getContents)(Codec.UTF8)
    try {
      assertEquals("Wrong result file content", "t1000919 success", source.mkString)
    } finally {
      source.close
    }

  }

  /**
   * Create a launch listener for launchTerminated events on a launch of the given launchConfiguration.
   */
  private def onLaunchTerminates(launchConfigurationName: String, f: () => Unit) = new ILaunchesListener2() {
    override def launchesTerminated(launches: Array[ILaunch]): Unit = {
      if (launches.exists(_.getLaunchConfiguration.getName == launchConfigurationName)) {
        f()
      }
    }
    override def launchesAdded(launches: Array[ILaunch]): Unit = {}
    override def launchesRemoved(launches: Array[ILaunch]): Unit = {}
    override def launchesChanged(launches: Array[ILaunch]): Unit = {}
  }

}