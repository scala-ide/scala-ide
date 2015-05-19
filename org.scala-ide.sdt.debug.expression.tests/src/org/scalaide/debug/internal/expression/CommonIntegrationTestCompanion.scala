/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import org.eclipse.core.internal.resources.ResourceException
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.AfterClass
import org.junit.BeforeClass
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.debug.internal.ScalaDebugRunningTest
import org.scalaide.debug.internal.ScalaDebugTestSession
import org.scalaide.logging.HasLogger

class CommonIntegrationTestCompanion(projectName: String) extends HasLogger {

  val eclipseProjectContext = EclipseProjectContext.provider.createContext(projectName)
  var session: ScalaDebugTestSession = null

  protected def withDebuggingSession[T](launchConfigurationName: String)(operation: ScalaDebugTestSession => T): T =
    eclipseProjectContext.withDebuggingSession(launchConfigurationName)(operation)

  private val testName = getClass.getSimpleName.init

  @BeforeClass
  def setup(): Unit = {
    logger.info(s"Test $testName started")
  }

  @AfterClass
  def doCleanup(): Unit = {
    logger.info(s"Test $testName finished")
    cleanDebugSession()
    eclipseProjectContext.cleanProject()
  }

  protected def refreshBinaryFiles(): Unit = eclipseProjectContext.refreshBinaries()

  protected def initializeEvaluator(session: ScalaDebugTestSession): JdiExpressionEvaluator = {
    val target = session.debugTarget
    new JdiExpressionEvaluator(target.classPath)
  }

  private def cleanDebugSession(): Unit = {
    if (session ne null) {
      session.terminate()
      session = null
    }
    eclipseProjectContext.cleanDebugSession()
  }
}