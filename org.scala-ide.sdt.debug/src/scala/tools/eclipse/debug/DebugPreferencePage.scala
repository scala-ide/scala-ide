package scala.tools.eclipse.debug

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.{FieldEditorPreferencePage, BooleanFieldEditor}
import org.eclipse.ui.{IWorkbenchPreferencePage, IWorkbench}

import DebugPreferencePage.P_ENABLE

class DebugPreferencePage extends FieldEditorPreferencePage with IWorkbenchPreferencePage {
  import DebugPreferencePage._

  setPreferenceStore(ScalaDebugPlugin.plugin.getPreferenceStore)
  setDescription("""Experimental debugger for Scala.
To use it, launch your Scala application as usual.""")

  override def createFieldEditors() {
    addField(new BooleanFieldEditor(P_ENABLE, "Enable (change will be applied to new debug sessions only)", getFieldEditorParent))
  }

  def init(workbench: IWorkbench) {}

}

object DebugPreferencePage {
  val BASE = "scala.tools.eclipse.debug."
  val P_ENABLE = BASE + "enabled"
}

class DebugPreferenceInitializer extends AbstractPreferenceInitializer {

  import DebugPreferencePage._

  override def initializeDefaultPreferences() {
    val store = ScalaDebugPlugin.plugin.getPreferenceStore
    store.setDefault(P_ENABLE, false)
  }

}