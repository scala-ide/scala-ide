package org.scalaide.debug.internal.launching

import scala.io.Codec
import scala.io.Source
import scala.util.control.Exception.allCatch

import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.ILaunchManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.scalaide.core.testsetup.IProjectHelpers
import org.scalaide.core.testsetup.TestProjectSetup

import ScalaApplicationLaunchConfigurationDelegateTest.file
import ScalaApplicationLaunchConfigurationDelegateTest.project

object ScalaApplicationLaunchConfigurationDelegateTest
  extends TestProjectSetup("launchDelegate", bundleName = "org.scala-ide.sdt.debug.tests")

class ScalaApplicationLaunchConfigurationDelegateTest extends LaunchUtils with IProjectHelpers {
  import ScalaApplicationLaunchConfigurationDelegateTest._
  override val launchConfigurationName = "launchDelegate"

  @Before def setup(): Unit = {
    cleanBuild(project)
  }

  @Test def shouldLaunchTestInRunMode(): Unit = {
    whenApplicationWasLaunchedFor(project, ILaunchManager.RUN_MODE) {
      assertSideEffect(ILaunchManager.RUN_MODE)
    }
  }

  @Test def shouldLaunchTestInDebugMode(): Unit = {
    whenApplicationWasLaunchedFor(project, ILaunchManager.DEBUG_MODE) {
      assertSideEffect(ILaunchManager.DEBUG_MODE)
    }
  }

  private def assertSideEffect(inMode: String) = {
    project.refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor)
    val resultFile = file("launchDelegate.result")
    if (resultFile.exists) {
      val source = Source.fromInputStream(resultFile.getContents)(Codec.UTF8)
      import scala.util.control.Exception._
      val actual = allCatch.andFinally(source.close) opt source.mkString
      Assert.assertEquals("Wrong result file content", "success", actual.getOrElse("failure"))
    } else {
      Assert.fail(s"result file not found in mode '$inMode'")
    }
  }
}