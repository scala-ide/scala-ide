package scala.tools.eclipse.scalatest.launching

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Test
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchManager
import org.junit.BeforeClass

class ScalaTestLaunchTest {

  import ScalaTestProject._
  
  private def launch(launchName: String) {
    val launchConfig = DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(file(launchName + ".launch"))
    launchConfig.launch(ILaunchManager.RUN_MODE, null)
  }
  
  @Test
  def testLaunchPackage() {
    launch("com.test")
  }
  
  @Test
  def testLaunchFile() {
    launch("SingleSpec.scala")
    launch("MultiSpec.scala")
  }
  
  @Test
  def testLaunchSuite() {
    launch("SingleSpec")
    launch("StackSpec2")
    launch("TestingFreeSpec")
    launch("TestingFunSuite")
  }
  
  @Test
  def testLaunchTest() {
    launch("AStackshouldtastelikepeanutbutter")
    launch("AStackwhenemptyshouldcomplainonpop")
    launch("AStackwhenfull")
    launch("AStackwheneveritisemptycertainlyoughttocomplainonpeek")
    launch("AStackwheneveritisempty")
    launch("AStack")
    launch("com.test.TestingFunSuite-'test2'")
  }
  
  // These tests requires jar file for specs1 and scalachecks wrapper runner, 
  // which is not in any public maven repo yet.  We could enable them back 
  // when they are in public maven repo.
  /*@Test
  def testLaunchSpec1() {
    launch("ExampleSpec1.scala")
    launch("ExampleSpec1")
    launch("Mysystem")
    launch("Mysystemalsocanprovidesadvancedfeature1")
  }
  
  @Test
  def testLaunchScalaCheck() {
    launch("StringSpecification.scala")
    launch("StringSpecification")
    launch("com.test.StringSpecification-'substring1'")
  }*/
}