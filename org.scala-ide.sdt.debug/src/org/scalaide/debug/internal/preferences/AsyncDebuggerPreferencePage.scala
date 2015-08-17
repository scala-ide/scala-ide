package org.scalaide.debug.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.StringFieldEditor
import org.eclipse.swt.layout.GridLayout
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.debug.internal.ScalaDebugPlugin

class AsyncDebuggerPreferencePage extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID) with IWorkbenchPreferencePage {
  import AsyncDebuggerPreferencePage._

  setPreferenceStore(ScalaDebugPlugin.plugin.getPreferenceStore)

  override def createFieldEditors(): Unit = {
    val c = getFieldEditorParent
    c.setLayout(new GridLayout(1, false))

    val fp = new StringFieldEditor(FadingPackages, "Comma separated list of packages that should be faded: ", c)
    addField(fp)
  }

  override def init(workbench: IWorkbench): Unit = {}

}

object AsyncDebuggerPreferencePage {
  val FadingPackages = "org.scalaide.debug.async.fadingPackages"
}

class AsyncDebuggerPreferencesInitializer extends AbstractPreferenceInitializer {
  import AsyncDebuggerPreferencePage._

  override def initializeDefaultPreferences(): Unit = {
    val store = ScalaDebugPlugin.plugin.getPreferenceStore

    store.setDefault(FadingPackages, "scala.,akka.,play.")
  }

}
