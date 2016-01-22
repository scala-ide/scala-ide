package org.scalaide.debug.internal.launching


import scala.io.Codec
import scala.io.Source

import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.ILaunchManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.scalaide.core.testsetup.SdtTestConstants
import org.scalaide.core.testsetup.TestProjectSetup

import LibraryJarInBootstrapTest.file
import LibraryJarInBootstrapTest.project

object LibraryJarInBootstrapTest extends TestProjectSetup("launching-1000919", bundleName = "org.scala-ide.sdt.debug.tests")

class LibraryJarInBootstrapTest extends LaunchUtils {
  import LibraryJarInBootstrapTest._
  override val launchConfigurationName = "t1000919.ScalaTest"

  @Before
  def initializeTests(): Unit = {
    cleanBuild(project.underlying)
  }

  /**
   * Ticket 1000919.
   * Before the fix, if order of the Scala library container and the JRE container was 'wrong', the Scala library was not added
   * to the boot classpath for test execution (nor the classpath) and the launch would fail.
   */
  @Ignore(SdtTestConstants.TestRequiresGuiSupport)
  @Test
  def checkTestIsCorrectlyExecutedWhenLibraryJarAfterJRE(): Unit = {
    whenApplicationWasLaunchedFor(project.underlying, inMode = ILaunchManager.RUN_MODE) {
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
  }

}
