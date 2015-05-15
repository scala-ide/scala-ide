package org.scalaide.core.compiler.settings

import org.scalaide.core.IScalaPlugin
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.scalaide.ui.internal.preferences.PropertyStore
import org.scalaide.core.testsetup.TestProjectSetup
import org.junit.Assert._
import org.junit.Test
import org.junit.BeforeClass
import org.junit.AfterClass
import org.scalaide.core.IScalaProject
import org.eclipse.ui.preferences.ScopedPreferenceStore
import org.eclipse.core.resources.ProjectScope
import org.eclipse.core.runtime.preferences.InstanceScope
import org.junit.After
import org.scalaide.ui.internal.preferences.PropertyStore
import org.eclipse.core.runtime.preferences.ConfigurationScope
import org.eclipse.core.runtime.Platform
import org.osgi.service.prefs.Preferences
import scala.tools.nsc.Settings
import org.scalaide.core.SdtConstants
import org.scalaide.core.testsetup.SDTTestUtils

object CompilerSettingsTest {
  private var project: IScalaProject = _

  @BeforeClass
  def createProject(): Unit = {
    project = SDTTestUtils.createProjectInWorkspace("compiler-settings", true)
  }

  @AfterClass
  def deleteProject(): Unit = {
    SDTTestUtils.deleteProjects(project)
  }
}

/** Note that project.scalacArguments feeds itself from ScalaProject.storage,
 *  which returns a project-scoped store or the instance-scoped workspace store,
 *  depending on the value of the useProjectSettings. @see `enableProjectSettings`.
 */
class CompilerSettingsTest {
  import CompilerSettingsTest.project
  val projectScope = new ProjectScope(project.underlying)

  private def prefStore = IScalaPlugin().getPreferenceStore()

  @After
  def clean_deprecation_and_additional(): Unit = {
    prefStore.setToDefault(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)
    prefStore.setToDefault("deprecation")
    prefStore.setToDefault(CompilerSettings.ADDITIONAL_PARAMS)

    val projectStore = new ScopedPreferenceStore(projectScope, SdtConstants.PluginId)
    projectStore.setToDefault(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)
    projectStore.setToDefault("deprecation")
    projectStore.setToDefault(CompilerSettings.ADDITIONAL_PARAMS)
  }

  // independent from PropertyStore, checks project-scoped reads find the instance scope
  @Test
  def import_from_instance_scope_to_project_scope(): Unit = {
    setWorkspaceSettings("deprecation", "true") // in essence writing to an instance-scoped store
    val projectStore = new ScopedPreferenceStore(projectScope, SdtConstants.PluginId)
    // TODO: This line is done by default in Kepler, remove it when we drop Juno
    projectStore.setSearchContexts(Array(projectScope, InstanceScope.INSTANCE, ConfigurationScope.INSTANCE))
    assertTrue("Settings should contain deprecation setting fetched from instance scope: " + project.scalacArguments, projectStore.getString("deprecation") == "true")
  }

  // unobviously independent from PropertyStore, checks project-scoped reads find project-scoped writes
  @Test
  def import_from_propertystore_to_project_scope(): Unit = {
    setProjectSettings("deprecation", "true")
    val projectStore = new ScopedPreferenceStore(projectScope, SdtConstants.PluginId)
    // TODO: This line is done by default in Kepler, remove it when we drop Juno
    projectStore.setSearchContexts(Array(projectScope, InstanceScope.INSTANCE, ConfigurationScope.INSTANCE))
    assertTrue("Settings should contain deprecation setting: " + project.scalacArguments, projectStore.getString("deprecation") == "true")
  }

  // unobviously independent from PropertyStore, checks instance-scoped reads don't find project-scoped writes
  @Test
  def no_import_from_propertystore_to_instance_scope(): Unit = {
    setProjectSettings("deprecation", "true")
    val instanceStore = new ScopedPreferenceStore(InstanceScope.INSTANCE, SdtConstants.PluginId)
    assertFalse("Settings should not contain deprecation setting: " + project.scalacArguments, instanceStore.getString("deprecation") == "true")
  }

  // independent from PropertyStore, checks instance-scoped reads don't find project-scoped writes
  @Test
  def no_import_from_projectscope_to_instance_scope(): Unit = {
    val projectStore = new ScopedPreferenceStore(projectScope, SdtConstants.PluginId)
    projectStore.setValue("deprecation", "true")
    val instanceStore = new ScopedPreferenceStore(InstanceScope.INSTANCE, SdtConstants.PluginId)
    assertFalse("Settings should not contain deprecation setting: " + project.scalacArguments, instanceStore.getString("deprecation") == "true")
  }

  @Test
  def instance_settings_need_no_flag(): Unit = {
    enableProjectSettings(false)
    setWorkspaceSettings("deprecation", "true")
    assertTrue("Settings should contain -deprecation after disabled write: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))
  }

  @Test
  def project_settings_do_need_the_flag(): Unit = {
    enableProjectSettings(false)
    setProjectSettings("deprecation", "true")
    assertFalse("Settings should not contain -deprecation after disabled write: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))
  }

  @Test
  def project_settings_really_do_need_the_flag(): Unit = {
    setProjectSettings("deprecation", "true")
    assertFalse("Settings should not contain -deprecation after not enabled write: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))
  }

  @Test
  def project_settings_need_the_flag(): Unit = {
    // note this (with other tests) show there is no write from the property store to the instance scope
    // the ScalaProject just returns the project-scoped store sometimes
    enableProjectSettings()
    checkProjectSettingsEnabled()
    setProjectSettings("deprecation", "true")
    assertTrue("Settings should contain -deprecation after enabled write: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))
  }

  // Beware of default settings in this case, see `PropertyStore#setValue`
  @Test
  def property_store_is_not_a_snapshot_anymore(): Unit = {
    enableProjectSettings()

    // just setProjectSettings("deprecation", "true"), keeping a handle on the store
    val projectStore = new ScopedPreferenceStore(projectScope, SdtConstants.PluginId)
    // TODO: This line is done by default in Kepler, remove it when we drop Juno
    projectStore.setSearchContexts(Array(projectScope, InstanceScope.INSTANCE, ConfigurationScope.INSTANCE))
    projectStore.setValue("deprecation", "true")
    projectStore.save()
    checkProjectSettingsEnabled()
    setProjectSettings("deprecation", "false")
    assertFalse("Settings should not contain -deprecation: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))
    assertTrue("ProjectStore should reflect exterior updates", projectStore.getString("deprecation") == "false")
  }

  @Test
  def project_settings_import_workspace_settings(): Unit = {
    enableProjectSettings()

    setWorkspaceSettings("deprecation", "true")
    assertTrue("Settings should contain -deprecation: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))

    checkProjectSettingsEnabled()
    setProjectSettings("deprecation", "false")
    assertFalse("Settings should not contain -deprecation: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))
  }

  @Test
  def project_settings_are_updated(): Unit = {
    enableProjectSettings()
    checkProjectSettingsEnabled()
    setProjectSettings("deprecation", "true")
    assertTrue("Settings should contain -deprecation: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))

    setProjectSettings("deprecation", "false")
    assertFalse("Settings should not contain -deprecation: " + project.scalacArguments, project.scalacArguments.contains("-deprecation"))
  }

  @Test
  def project_additional_settings_are_updated(): Unit = {
    enableProjectSettings()
    checkProjectSettingsEnabled()
    setProjectSettings(CompilerSettings.ADDITIONAL_PARAMS, "-language:implicits")
    assertTrue("Settings should contain additional parameters: " + project.scalacArguments, project.scalacArguments.contains("-language:implicits"))

    setProjectSettings(CompilerSettings.ADDITIONAL_PARAMS, "-Ylog:typer")
    assertFalse("Settings should not contain additional parameters: " + project.scalacArguments, project.scalacArguments.contains("-language:implicits"))
    assertTrue("Settings should contain additional parameters: " + project.scalacArguments, project.scalacArguments.contains("-Ylog:typer"))
  }

  @Test
  def no_javaextdirs(): Unit = {
    val scalacArgs = project.scalacArguments

    // We make sure -javaextdirs never picks up the default (runtime) JRE
    // See ticket #1002072
    project.presentationCompiler { comp =>
      val settings = new Settings
      settings.processArguments(scalacArgs.toList, true)
      val resolver = new scala.tools.util.PathResolver(settings)
      assertEquals("Calculated javaextdirs should be empty", "", resolver.Calculated.javaExtDirs.trim)
    }
  }

  private def enableProjectSettings(value: Boolean = true): Unit = {
    val projectStore = new PropertyStore(projectScope, SdtConstants.PluginId)
    projectStore.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, value)
    projectStore.save()
  }

  private def checkProjectSettingsEnabled(): Unit ={
    val projectStore = new ScopedPreferenceStore(projectScope, SdtConstants.PluginId)
    // TODO: This line is done by default in Kepler, remove it when we drop Juno
    projectStore.setSearchContexts(Array(projectScope, InstanceScope.INSTANCE, ConfigurationScope.INSTANCE))
    assertTrue("project-specific settings should be enabled at this stage", projectStore.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE))
  }

  /** Set a workspace-wide setting value. For compiler settings, you need to strip the '-', for instance
   *  call `setWorkspaceSettings("deprecation", ..") instead of "-deprecation"
   */
  private def setWorkspaceSettings(settingName: String, value: String): Unit = {
    // this writes to the plugin's Instance-level preference Store
    prefStore.setValue(settingName, value)
  }

  /** Set a project-scoped setting value. For compiler settings, you need to strip the '-', for instance
   *  call `setWorkspaceSettings("deprecation", ..") instead of "-deprecation"
   */
  private def setProjectSettings(settingName: String, value: String): Unit = {
    val projectStore = new PropertyStore(projectScope, SdtConstants.PluginId)
    projectStore.setValue(settingName, value)
    projectStore.save()// the project store is an in-memory snapshot, needs to be persisted this way
  }
}
