package scala.tools.eclipse.launching

import scala.tools.eclipse.debug.ScalaDebugPlugin
import org.junit.Before
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Test
import org.junit.Assert._
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchManager
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path
import scala.io.Source
import scala.io.Codec
import org.eclipse.core.resources.IResource

object LibraryJarInBootstrapTest extends TestProjectSetup("launching-1000919", bundleName = "org.scala-ide.sdt.debug.tests")

class LibraryJarInBootstrapTest {
  
  import LibraryJarInBootstrapTest._
  
  @Before
  def initializeTests() {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }
  
  /**
   * Ticket 1000919.
   * Before the fix, if order of the Scala library container and the JRE container was 'wrong', the Scala library was not added
   * to the boot classpath for test execution (nor the classpath) and the launch would fail.
   */
  @Test
  def checkTestIsCorrectlyExecutedWhenLibraryJarAfterJRE() {
    // launch the saved launch configuration
    val launchConfiguration = DebugPlugin.getDefault.getLaunchManager.getLaunchConfiguration(file("t1000919.ScalaTest.launch"))
    val launch= launchConfiguration.launch(ILaunchManager.RUN_MODE, null)
    
    // wait for it to terminates
    while (!launch.isTerminated) {
      Thread.sleep(250)
    }
    
    // check the result
    implicit val codec= Codec.UTF8
    // refresh the project to be able to see the new file
    project.underlying.refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor)
    val resultFile = file("t1000919.result")
    assertTrue("No result file, the launched test likely failed to run", resultFile.exists)
    
    // check the content
    val source= Source.fromInputStream(resultFile.getContents)
    try {
      assertEquals("Wrong result file content", "t1000919 success", source.mkString)
    } finally {    
      source.close
    }
  
  }

}