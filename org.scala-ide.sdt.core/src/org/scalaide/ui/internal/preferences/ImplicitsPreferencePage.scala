package org.scalaide.ui.internal.preferences

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

  override def createFieldEditors(): Unit = {
    addBooleanFieldEditors(
      PActive -> "Enable implicit highlighting",
      PBold -> "Highlight in bold",
      PItalic -> "Highlight in italic",
      PConversionsOnly -> "Only highlight implicit conversions",
      PFirstLineOnly -> "Only highlight the first line in an implicit conversion")
  }
}

object ImplicitsPreferencePage {
  val PActive = "scala.tools.eclipse.ui.preferences.implicit.enabled"
  val PBold = "scala.tools.eclipse.ui.preferences.implicit.text.bold"
  val PItalic = "scala.tools.eclipse.ui.preferences.implicit.text.italic"
  val PConversionsOnly = "scala.tools.eclipse.ui.preferences.implicit.conversions.only"
  val PFirstLineOnly = "scala.tools.eclipse.ui.preferences.implicit.firstline.only"
}

class ImplicitsPagePreferenceInitializer extends AbstractPreferenceInitializer {

  import ImplicitsPreferencePage._

  override def initializeDefaultPreferences(): Unit = {
    val store = IScalaPlugin().getPreferenceStore
    store.setDefault(PActive, true)
    store.setDefault(PBold, false)
    store.setDefault(PItalic, false)
    store.setDefault(PConversionsOnly, true)
    store.setDefault(PFirstLineOnly, true)
  }
}
