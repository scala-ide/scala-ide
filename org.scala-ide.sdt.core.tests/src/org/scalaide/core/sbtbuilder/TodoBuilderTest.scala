package org.scalaide.core
package sbtbuilder

import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert._
import org.junit.Before
import org.junit.Test
import org.scalaide.core.SdtConstants
import org.scalaide.core.testsetup.CustomAssertion
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.TestProjectSetup

object TodoBuilderTest extends TestProjectSetup("todobuilder") with CustomAssertion

class TodoBuilderTest {

  import TodoBuilderTest._

  @Before
  def setupWorkspace(): Unit =
    SDTTestUtils.enableAutoBuild(false)

  private def markers(inFile: String): Seq[IMarker] =
    compilationUnit(inFile)
      .getUnderlyingResource()
      .findMarkers(SdtConstants.TaskMarkerId, false, IResource.DEPTH_INFINITE)

  private def checkTasks(inFile: String, expectedMsgs: String*): Unit = {
    val markerMsgs = markers(inFile).map(_.getAttribute(IMarker.MESSAGE).toString)
    expectedMsgs.foreach { expectedMsg =>
      assertTrue(s"'$expectedMsg' not found in: ${markerMsgs.mkString(", ")}", markerMsgs.contains(expectedMsg))
    }
  }

  private def cleanBuild(): Unit = {
    project.clean(new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
  }

  @Test def todoInScalaFile(): Unit = {
    cleanBuild()
    checkTasks(inFile = "test/foo/ClassA.scala", expectedMsgs = "TODO todo in Scala file", "FIXME fixme in Scala file")
  }
}
