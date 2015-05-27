package org.scalaide.debug.internal.launching

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunch
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.ILaunchesListener2

/**
 * Used for launching application.
 */
trait LaunchUtils {
  /** Points to launch configuration file. */
  val launchConfigurationName: String

  private val DefaultMonitor = new NullProgressMonitor

  /** Create a launch listener for launchTerminated events on a launch of the given launchConfiguration. */
  def onLaunchTerminates(f: () => Unit) = new ILaunchesListener2() {
    override def launchesTerminated(launches: Array[ILaunch]): Unit = {
      if (launches.exists(_.getLaunchConfiguration.getName == launchConfigurationName)) {
        f()
      }
    }
    override def launchesAdded(launches: Array[ILaunch]): Unit = {}
    override def launchesRemoved(launches: Array[ILaunch]): Unit = {}
    override def launchesChanged(launches: Array[ILaunch]): Unit = {}
  }

  /** Cleans and incrementally builds projects */
  def cleanBuild(projects: IProject*): Unit = projects.foreach { project =>
    project.build(IncrementalProjectBuilder.CLEAN_BUILD, DefaultMonitor)
    project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, DefaultMonitor)
  }

  private def launchConfiguration(project: IProject): ILaunchConfiguration =
    DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(project.getFile(launchConfigurationName + ".launch"))

  def whenApplicationWasLaunchedFor(project: IProject, inMode: String)(inThatCase: => Unit): Unit = {
    val latch = new CountDownLatch(1)
    DebugPlugin.getDefault.getLaunchManager.addLaunchListener(onLaunchTerminates(latch.countDown))
    val lc = launchConfiguration(project)
    val launch = lc.launch(inMode, DefaultMonitor)
    val timeout = if (launch.canTerminate) 10 else 60
    latch.await(timeout, TimeUnit.SECONDS)
    if (launch.canTerminate && !launch.isTerminated) {
      throw new IllegalStateException(s"launch did not terminate in ${timeout}s")
    }
    inThatCase
  }
}