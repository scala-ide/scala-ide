package scala.tools.eclipse
package compiler.settings

import scala.tools.nsc.plugins.Plugin

import org.junit.Test
import org.junit.Assert

object CompilerSettingsTest {
  private val simulator = new EclipseUserSimulator
  lazy val projectName = "test_settings"
  lazy val project = simulator.createProjectInWorkspace(projectName, false)
}

class CompilerSettingsTest {
  import CompilerSettingsTest._
  
  @Test
  def continuations_plugin_works() {
    Assert.assertEquals("Loaded plugins: ", List("continuations"), loadedPlugins(project))
  }
  
  @Test
  def pluginsDir_does_not_break_continuations() {
    project.storage.setValue("Xpluginsdir", "/doesnotexist")
    project.resetPresentationCompiler()
    
    Assert.assertEquals("Loaded plugins: ", List("continuations"), loadedPlugins(project))
  }
  
  private def loadedPlugins(project: ScalaProject): List[String] = {
    val plugins = project.withPresentationCompiler(comp => comp.plugins)(List[Plugin]())
    plugins.map(_.name)
  }
}
