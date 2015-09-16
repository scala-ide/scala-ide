package org.scalaide.debug.internal.launching

import org.eclipse.debug.core.ILaunchManager
import org.junit.After
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
  private val fileWithLaunchEffectName = "launchDelegate.result"

  @Before def setup(): Unit = {
    cleanBuild(project)
  }

  @After def clean(): Unit = {
    file(fileWithLaunchEffectName).delete( /*force = */ true, /*monitor = */ null)
  }

  @Test def shouldLaunchTestInDebugMode(): Unit = {
    whenApplicationWasLaunchedFor(project, ILaunchManager.DEBUG_MODE) {
      assertLaunchEffect(project, ILaunchManager.DEBUG_MODE, file(fileWithLaunchEffectName))
    }
  }
}