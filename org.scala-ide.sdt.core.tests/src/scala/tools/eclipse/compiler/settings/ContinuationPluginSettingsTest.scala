package scala.tools.eclipse
package compiler.settings

import scala.tools.nsc.plugins.Plugin
import org.junit.Test
import org.junit.Assert
import java.io.File
import org.junit.Ignore
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.osgi.framework.Version
import org.junit.AfterClass

object ContinuationPluginSettingsTest {
  private val simulator = new EclipseUserSimulator
  lazy val projectName = "test_settings"
  lazy val project = {
    val p =simulator.createProjectInWorkspace(projectName, false)
    val prefs = p.projectSpecificStorage
    prefs.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)
    prefs.save()
    p
  }

  private def setPrefToDefault(key: String) {
    val prefs = project.projectSpecificStorage
    prefs.setToDefault(key)
    prefs.save()
  }

  private def setPrefValue(key: String, value: String) {
    val prefs = project.projectSpecificStorage
    prefs.setValue(key, value)
    prefs.save()
  }

  private def setPrefValue(key: String, value: Boolean) {
    val prefs = project.projectSpecificStorage
    prefs.setValue(key, value)
    prefs.save()
  }

  @AfterClass
  def deleteTestProject {
    project.underlying.delete(true, null)
  }

}

class ContinuationPluginSettingsTest {
  import ContinuationPluginSettingsTest._

  @Test
  def continuationsPluginIsAlwaysLoaded() {
    setPrefToDefault("Xpluginsdir")
    setPrefToDefault("Xplugin")
    forceEnableContinuationForNewerScalaVersion()
    val plugins = loadedPlugins(project)
    Assert.assertEquals("Loaded plugins: ", List("continuations"), loadedPlugins(project))
  }

  @Test
  def loadContinuationsPluginVia_XpluginsdirCompilerSetting() {
    setPrefValue("Xpluginsdir", ScalaPlugin.plugin.defaultPluginsDir)
    setPrefValue("Xplugin", "/doesnotexits")
    forceEnableContinuationForNewerScalaVersion()
    val plugins = loadedPlugins(project)
    Assert.assertEquals("Loaded plugins: ", List("continuations"), loadedPlugins(project))
  }

  @Test
  def loadContinuationsPluginVia_XpluginCompilerSetting() {
    setPrefValue("Xpluginsdir", "/doesnotexist")
    setPrefValue("Xplugin", ScalaPlugin.plugin.defaultPluginsDir + File.separator + "continuations.jar")
    forceEnableContinuationForNewerScalaVersion()
    val plugins = loadedPlugins(project)
    Assert.assertEquals("Loaded plugins: ", List("continuations"), loadedPlugins(project))
  }

  @Test
  def continuationPluginCannotBeLoadedWhen_pluginsDir_pointsToDirectoryThatDoesNotContainContinuationsPluginJar() {
    setPrefValue("Xpluginsdir", "/doesnotexist")
    setPrefValue("Xplugin", "/doesnotexits")
    forceEnableContinuationForNewerScalaVersion()

    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    Assert.assertEquals("Loaded plugins: ", Nil, loadedPlugins(project))
  }

  private def loadedPlugins(project: ScalaProject): List[String] = {
    val plugins = project.withPresentationCompiler(comp => comp.plugins)(List[Plugin]())
    plugins.map(_.name)
  }

  private def forceEnableContinuationForNewerScalaVersion() {
    if (TestUtil.installedScalaVersionGreaterOrEqualsTo(new Version(2, 11, 0)))
      setPrefValue("P", "continuations:enable")
    project.presentationCompiler.askRestart()
  }

}
