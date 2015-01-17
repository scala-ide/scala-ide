package org.scalaide.ui.internal.preferences

import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.scalaide.core.IScalaPlugin
import org.scalaide.util.eclipse.SWTUtils

class CallByNameParamCreationPreferencePage extends BasicFieldEditorPreferencePage("Configure highlighting for the creation of call-by-name parameters") {
  import CallByNameParamCreationPreferencePage._

  override def createContents(parent: Composite): Control = {
    val control = super.createContents(parent).asInstanceOf[Composite]
    SWTUtils.mkLinkToAnnotationsPref(parent)(a => s"More options for highlighting for call-by-name parameters on the $a preference page.")
    control
  }

  override def createFieldEditors() {
    addBooleanFieldEditors(
      P_ACTIVE -> "Enabled",
      P_BOLD -> "Bold",
      P_ITALIC -> "Italic",
      P_FIRST_LINE_ONLY -> "Only highlight the first line")
  }
}

object CallByNameParamCreationPreferencePage {
  val P_ACTIVE = "scala.tools.eclipse.ui.preferences.callByNameParamCreation.enabled"
  val P_BOLD = "scala.tools.eclipse.ui.preferences.callByNameParamCreation.text.bold"
  val P_ITALIC = "scala.tools.eclipse.ui.preferences.callByNameParamCreation.text.italic"
  val P_FIRST_LINE_ONLY  = "scala.tools.eclipse.ui.preferences.callByNameParamCreation.firstline.only"
}

class CallByNameParamCreationPreferenceInitializer extends AbstractPreferenceInitializer {
  import CallByNameParamCreationPreferencePage._

  override def initializeDefaultPreferences() {
    val store = IScalaPlugin().getPreferenceStore
    store.setDefault(P_ACTIVE, true)
    store.setDefault(P_BOLD, false)
    store.setDefault(P_ITALIC, false)
    store.setDefault(P_FIRST_LINE_ONLY, true)
  }
}
