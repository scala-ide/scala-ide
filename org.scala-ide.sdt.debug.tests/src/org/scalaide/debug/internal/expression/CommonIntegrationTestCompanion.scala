/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import org.eclipse.core.internal.resources.ResourceException
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.AfterClass
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.debug.internal.ScalaDebugRunningTest
import org.scalaide.debug.internal.ScalaDebugTestSession

class CommonIntegrationTestCompanion(workspace: String = "expression")
  extends TestProjectSetup(workspace, bundleName = "org.scala-ide.sdt.debug.tests")
  with ScalaDebugRunningTest {

  var session: ScalaDebugTestSession = null

  protected def initDebugSession(launchConfigurationName: String): ScalaDebugTestSession =
    ScalaDebugTestSession(file(launchConfigurationName + ".launch"))

  @AfterClass
  def doCleanup(): Unit = {
    cleanDebugSession()
    deleteProject()
  }

  protected def refreshBinaryFiles(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }

  protected def initializeEvaluator(session: ScalaDebugTestSession): JdiExpressionEvaluator = {
    val target = session.debugTarget
    val thread = session.currentStackFrame.stackFrame.thread
    new JdiExpressionEvaluator(target, thread)
  }

  private def cleanDebugSession(): Unit = {
    if (session ne null) {
      session.terminate()
      session = null
    }
  }

  private def deleteProject(): Unit = {
    try {
      SDTTestUtils.deleteProjects(project)
    } catch {
      case e: ResourceException => // could not delete resource, but don't you worry ;)
    }
  }
}
