package scala.tools.eclipse.compiler.settings

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Test
import org.junit.Assert.{ assertTrue, assertFalse }
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.SettingConverterUtil
import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.properties.CompilerSettings
import scala.tools.eclipse.javaelements.ScalaSourceFile

object CompilerSettingsTest extends TestProjectSetup("compiler-settings")

class CompilerSettingsTest {
  import CompilerSettingsTest._

  @Test
  def presentation_compiler_report_errors_when_continuations_plugin_is_not_enabled() {
    val source = scalaCompilationUnit("cps/CPS.scala")
    openAndWaitUntilTypechecked(source)
    assertTrue(Option(source.getProblems).toList.nonEmpty)
  }

  @Test
  def failingToBuildSourceThatRequiresContinuationPlugin() {
    val unit = scalaCompilationUnit("cps/CPS.scala")

    cleanProject()
    fullProjectBuild()

    val errors = allBuildErrorsOf(unit)

    assertTrue(errors.nonEmpty)
  }

  @Test
  def presentation_compiler_does_not_report_errors_when_continuations_plugin_is_enabled(): Unit = withContinuationPluginEnabled {
    val source = scalaCompilationUnit("cps/CPS.scala")
    openAndWaitUntilTypechecked(source)
    assertTrue(Option(source.getProblems).toList.isEmpty)
  }

  @Test
  def successfullyBuildingSourceRequiringContinuationPluginEnabled() {
    withContinuationPluginEnabled {
      val unit = scalaCompilationUnit("cps/CPS.scala")

      cleanProject()
      fullProjectBuild()

      val errors = allBuildErrorsOf(unit)

      assertTrue(errors.isEmpty)
    }
  }

  @Test
  def workspace_settings_are_correctly_propagated() {
    enableProjectSettings()

    try {
      setWorkspaceSettings("deprecation", "true")
      assertTrue("Settings should contain -deprecation: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))

      setProjectSettings("deprecation", "false")
      assertFalse("Settings should not contain -deprecation: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))
    } finally {
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

  private def enableProjectSettings() {
    val projectStore = new PropertyStore(project.underlying, ScalaPlugin.prefStore, ScalaPlugin.plugin.pluginId)
    projectStore.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)
    projectStore.save()
  }

  /** Set a workspace-wide setting value. For compiler settings, you need to strip the '-', for instance
   *  call `setWorkspaceSettings("deprecation", ..") instead of "-deprecation"
   */
  private def setWorkspaceSettings(settingName: String, value: String) {
    ScalaPlugin.prefStore.setValue(settingName, value)
  }

  /** Set a project-scoped setting value. For compiler settings, you need to strip the '-', for instance
   *  call `setWorkspaceSettings("deprecation", ..") instead of "-deprecation"
   */
  private def setProjectSettings(settingName: String, value: String) {
    val projectStore = new PropertyStore(project.underlying, ScalaPlugin.prefStore, ScalaPlugin.plugin.pluginId)
    projectStore.setValue(settingName, value)
    projectStore.save() // the project store is an in-memory snapshot, needs to be persisted this way
  }

  private def withContinuationPluginEnabled(body: => Unit) {
    val value = project.storage.getString("P")
    try {
      project.storage.setValue("P", "continuations:enable")
      body
    } finally {
      project.storage.setValue("P", value)
    }
  }
}
