package org.scalaide.ui.internal.preferences

import org.eclipse.jface.preference._
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.IWorkbench
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.IPreferenceStore
import org.scalaide.core.IScalaPlugin
import org.eclipse.swt.widgets.Link
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.jdt.internal.ui.preferences.PreferencesMessages
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.ui.dialogs.PreferencesUtil
import org.scalaide.util.eclipse.SWTUtils

class ImplicitsPreferencePage extends BasicFieldEditorPreferencePage("Set the highlighting for implicit conversions and implicit parameters") {
  import ImplicitsPreferencePage._

  override def createContents(parent: Composite): Control = {
    val control = super.createContents(parent).asInstanceOf[Composite]
    SWTUtils.mkLinkToAnnotationsPref(parent)(a => s"More options for highlighting for implicit conversions on the $a preference page.")
    control
  }

  override def createFieldEditors() {
    addBooleanFieldEditors(
      P_ACTIVE -> "Enabled",
      P_BOLD -> "Bold",
      P_ITALIC -> "Italic",
      P_CONVERSIONS_ONLY -> "Only highlight implicit conversions",
      P_FIRST_LINE_ONLY -> "Only highlight the first line in an implicit conversion")
  }
}

object ImplicitsPreferencePage {
  val BASE = "scala.tools.eclipse.ui.preferences.implicit."
  val P_ACTIVE = BASE + "enabled"
  val P_BOLD = BASE + "text.bold"
  val P_ITALIC = BASE + "text.italic"
  val P_CONVERSIONS_ONLY = BASE + "conversions.only"
  val P_FIRST_LINE_ONLY  = BASE + "firstline.only"
}

class ImplicitsPagePreferenceInitializer extends AbstractPreferenceInitializer {

  import ImplicitsPreferencePage._

  override def initializeDefaultPreferences() {
    val store = IScalaPlugin().getPreferenceStore
    store.setDefault(P_ACTIVE, true)
    store.setDefault(P_BOLD, false)
    store.setDefault(P_ITALIC, false)
    store.setDefault(P_CONVERSIONS_ONLY, true)
    store.setDefault(P_FIRST_LINE_ONLY, true)
  }
}
