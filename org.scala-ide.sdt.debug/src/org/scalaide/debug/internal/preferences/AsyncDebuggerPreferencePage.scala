package org.scalaide.debug.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.ColorFieldEditor
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

    val color = new ColorFieldEditor(FadingColor, "Color of faded packages:", c)
    addField(color)
  }

  override def init(workbench: IWorkbench): Unit = {}

}

object AsyncDebuggerPreferencePage {
  val FadingPackages = "org.scalaide.debug.async.fadingPackages"
  val FadingColor = "org.scalaide.debug.async.fadingColor"
}

class AsyncDebuggerPreferencesInitializer extends AbstractPreferenceInitializer {
  import AsyncDebuggerPreferencePage._

  override def initializeDefaultPreferences(): Unit = {
    val store = ScalaDebugPlugin.plugin.getPreferenceStore

    store.setDefault(FadingPackages, "scala.,akka.,play.")
    store.setDefault(FadingColor, "191,191,191")
  }

}
