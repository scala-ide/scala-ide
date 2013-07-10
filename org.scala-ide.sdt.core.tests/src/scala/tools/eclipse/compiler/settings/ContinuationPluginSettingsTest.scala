package scala.tools.eclipse
package compiler.settings

import scala.tools.nsc.plugins.Plugin
import org.junit.Test
import org.junit.Assert
import java.io.File
import org.junit.Ignore
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor

object ContinuationPluginSettingsTest {
  private val simulator = new EclipseUserSimulator
  lazy val projectName = "test_settings"
  lazy val project = simulator.createProjectInWorkspace(projectName, false)
}

class ContinuationPluginSettingsTest {
  import ContinuationPluginSettingsTest._

  @Test
  def continuationsPluginIsAlwaysLoaded() {
    project.storage.setToDefault("Xpluginsdir")
    project.storage.setToDefault("Xplugin")
    val plugins = loadedPlugins(project)
    Assert.assertEquals("Loaded plugins: ", List("continuations"), loadedPlugins(project))
  }

  @Test
  def loadContinuationsPluginVia_XpluginsdirCompilerSetting() {
    project.storage.setValue("Xpluginsdir", ScalaPlugin.plugin.defaultPluginsDir)
    project.storage.setValue("Xplugin", "/doesnotexits")
    project.resetPresentationCompiler()
    val plugins = loadedPlugins(project)
    Assert.assertEquals("Loaded plugins: ", List("continuations"), loadedPlugins(project))
  }

  @Test
  def loadContinuationsPluginVia_XpluginCompilerSetting() {
    project.storage.setValue("Xpluginsdir", "/doesnotexist")
    project.storage.setValue("Xplugin", ScalaPlugin.plugin.defaultPluginsDir + File.separator + "continuations.jar")
    project.resetPresentationCompiler()
    val plugins = loadedPlugins(project)
    Assert.assertEquals("Loaded plugins: ", List("continuations"), loadedPlugins(project))
  }

  @Test
  def continuationPluginCannotBeLoadedWhen_pluginsDir_pointsToDirectoryThatDoesNotContainContinuationsPluginJar() {
    project.storage.setValue("Xpluginsdir", "/doesnotexist")
    project.storage.setValue("Xplugin", "/doesnotexits")
    project.resetPresentationCompiler()

    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    Assert.assertEquals("Loaded plugins: ", Nil, loadedPlugins(project))
  }

  private def loadedPlugins(project: ScalaProject): List[String] = {
    val plugins = project.withPresentationCompiler(comp => comp.plugins)(List[Plugin]())
    plugins.map(_.name)
  }
}
