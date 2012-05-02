package scala.tools.eclipse.scalatest

import org.junit.runner.RunWith
import org.junit.runners.Suite
import scala.tools.eclipse.scalatest.launching.ScalaTestLaunchableTesterTest
import scala.tools.eclipse.scalatest.launching.ScalaTestLaunchShortcutTest

@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(classOf[ScalaTestLaunchableTesterTest], 
        classOf[ScalaTestLaunchShortcutTest]))
class TestSuite 