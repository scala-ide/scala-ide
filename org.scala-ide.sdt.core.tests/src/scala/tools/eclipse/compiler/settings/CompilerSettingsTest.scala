package scala.tools.eclipse.compiler.settings

import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.SettingConverterUtil
import scala.tools.eclipse.properties.CompilerSettings
import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Assert._
import org.junit.Test
import org.junit.BeforeClass
import org.junit.AfterClass
import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.util.EclipseUtils

object CompilerSettingsTest {
  private val simulator = new EclipseUserSimulator
  private var project: ScalaProject = _

  @BeforeClass
  def createProject() {
    project = simulator.createProjectInWorkspace("compiler-settings", true)
  }

  @AfterClass
  def deleteProject() {
    EclipseUtils.workspaceRunnableIn(ScalaPlugin.plugin.workspaceRoot.getWorkspace) { _ =>
      project.underlying.delete(true, null)
    }
  }
}

class CompilerSettingsTest {
  import CompilerSettingsTest.project

  @Test
  def workspace_settings_are_correctly_propagated() {
    enableProjectSettings()

    try {
      setWorkspaceSettings("deprecation", "true")
      assertTrue("Settings should contain -deprecation: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))

      setProjectSettings("deprecation", "false")
      assertFalse("Settings should not contain -deprecation: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))
    }
    finally {
      setWorkspaceSettings("deprecation", "false")
    }
  }

  @Test
  def project_settings_are_correctly_updated() {
    enableProjectSettings()
    setProjectSettings("deprecation", "true")
    assertTrue("Settings should contain -deprecation: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))

    setProjectSettings("deprecation", "false")
    assertFalse("Settings should not contain -deprecation: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))
  }

  @Test
  def project_additional_settings_are_correctly_updated() {
    enableProjectSettings()
    setProjectSettings(CompilerSettings.ADDITIONAL_PARAMS, "-language:implicits")
    assertTrue("Settings should contain additional parameters: " + project.scalacArguments, project.scalacArguments.contains("-language:implicits"))

    setProjectSettings(CompilerSettings.ADDITIONAL_PARAMS, "-Ylog:typer")
    assertFalse("Settings should not contain additional parameters: " + project.scalacArguments, project.scalacArguments.contains("-language:implicits"))
    assertTrue("Settings should contain additional parameters: " + project.scalacArguments, project.scalacArguments.contains("-Ylog:typer"))
  }

  private def enableProjectSettings(value: Boolean = true) {
    val projectStore = new PropertyStore(project.underlying, ScalaPlugin.prefStore, ScalaPlugin.plugin.pluginId)
    projectStore.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, value)
    projectStore.save()
  }

  /** Set a workspace-wide setting value. For compiler settings, you need to strip the '-', for instance
    * call `setWorkspaceSettings("deprecation", ..") instead of "-deprecation"
    */
  private def setWorkspaceSettings(settingName: String, value: String) {
    ScalaPlugin.prefStore.setValue(settingName, value)
  }

  /** Set a project-scoped setting value. For compiler settings, you need to strip the '-', for instance
    * call `setWorkspaceSettings("deprecation", ..") instead of "-deprecation"
    */
  private def setProjectSettings(settingName: String, value: String) {
    val projectStore = new PropertyStore(project.underlying, ScalaPlugin.prefStore, ScalaPlugin.plugin.pluginId)
    projectStore.setValue(settingName, value)
    projectStore.save() // the project store is an in-memory snapshot, needs to be persisted this way
  }
}
