/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import java.util.HashMap
import org.eclipse.core.internal.resources.ResourceException
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.DebugPlugin
import org.junit.Assert.fail
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.debug.internal.ScalaDebugRunningTest
import org.scalaide.debug.internal.ScalaDebugTestSession
import org.scalaide.debug.internal.launching.RemoteConnector
import org.eclipse.debug.core.ILaunchConfiguration
import scala.collection.mutable
import scala.collection.JavaConverters._

/** Global state (due to JUnit architecture) holding information how provide eclipse project context for tests */
object EclipseProjectContext {
  var currentProvider: Option[EclipseProjectContextProvider] = None

  var isRemote = false

  def provider: EclipseProjectContextProvider = currentProvider.getOrElse(StandardContextProvider)

  type ScalaDebugProjectSetup = TestProjectSetup with ScalaDebugRunningTest with RemoteConnector
}

/** Eclipse project contexts factory for given projects */
trait EclipseProjectContextProvider {
  def createContext(projectName: String): EclipseProjectContext
}

/** Standard provider - compiles etc. project for each call */
object StandardContextProvider extends EclipseProjectContextProvider {
  override def createContext(projectName: String): EclipseProjectContext =
    new EclipseProjectContext(createSetup(projectName))

  def createSetup(projectName: String): TestProjectSetup with ScalaDebugRunningTest with RemoteConnector =
    new TestProjectSetup(projectName, bundleName = "org.scala-ide.sdt.debug.expression.tests") with ScalaDebugRunningTest with RemoteConnector
}

/** Responsible for managing remote session (starting debugged application), recompiling sources etc.s */
class EclipseProjectContext(setup: EclipseProjectContext.ScalaDebugProjectSetup) {

  /** Recompiles sources and fail test if compilation failed */
  def refreshBinaries(): Unit = {
    setup.project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    setup.project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
    val markers = setup.project.underlying.findMarkers( /*type =*/ null, /*includeSubtypes=*/ true, IResource.DEPTH_INFINITE)
    val problemMarkers = markers.filter(_.getAttribute(IMarker.SEVERITY) == IMarker.SEVERITY_ERROR)

    def printMarker(marker: IMarker): String = {
      val path = marker.getResource.getProjectRelativePath()
      val line = marker.getAttribute("lineNumber")
      val message = marker.getAttribute("message")
      s"($path: $line): $message"
    }

    if (!problemMarkers.isEmpty) {
      val markersText = problemMarkers.map(printMarker).mkString("\n")
      fail(s"Compilation failed. Problems:\n $markersText")
    }
  }

  /** Kills all running eclipse applications. Just to be sure. */
  def cleanDebugSession(): Unit = {
    val launches = DebugPlugin.getDefault.getLaunchManager().getLaunches().toSeq.filterNot(_.isTerminated)
    launches.foreach(_.terminate())
  }

  def cleanProject(): Unit = {
    try {
      SDTTestUtils.deleteProjects(setup.project)
    } catch {
      case e: ResourceException => // could not delete resource, but don't you worry ;)
    }
  }

  final def withDebuggingSession[T](fileName: String)(operation: ScalaDebugTestSession => T): T = {
    if (EclipseProjectContext.isRemote) withRemoteDebuggingSession(fileName)(operation)
    else operation(ScalaDebugTestSession(setup.file(fileName + ".launch")))
  }

  private def withRemoteDebuggingSession[T](fileName: String)(operation: ScalaDebugTestSession => T): T = {
    val port = 6016
    val testStartDelay = 500
    val remoteConnectionDelay = 50

    refreshBinaries()

    setup.launchInRunMode(fileName, port, testStartDelay)
    Thread.sleep(remoteConnectionDelay) //to be sure that project is started
    operation(ScalaDebugTestSession(createRemoteConnection(port)))
  }


  private def createRemoteConnection(port: Int): ILaunchConfiguration = {
    val remoteApplicationName = "org.eclipse.jdt.launching.remoteJavaApplication"
    val configurationName = "remote"
    val connectorKey = "org.eclipse.jdt.launching.VM_CONNECTOR_ID"
    val scalaConnectorName = "org.scala-ide.sdt.debug.socketAttachConnector"

    val connectionParametersKey = "org.eclipse.jdt.launching.CONNECT_MAP"

    val manager = DebugPlugin.getDefault.getLaunchManager

    val remoteCategory = manager.getLaunchConfigurationType(remoteApplicationName)

    val remoteDebugginfLaunchConfig =
      remoteCategory.newInstance(setup.project.underlying, configurationName)

    remoteDebugginfLaunchConfig.setAttribute(connectorKey, scalaConnectorName)
    val map = mutable.Map("hostname" -> "localhost", "port" -> port.toString).asJava

    remoteDebugginfLaunchConfig.setAttribute(connectionParametersKey, map)
    remoteDebugginfLaunchConfig
  }
}