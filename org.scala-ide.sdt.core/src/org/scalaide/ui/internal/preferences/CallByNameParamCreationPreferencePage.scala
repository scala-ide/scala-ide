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
      PActive -> "Enabled",
      PBold -> "Bold",
      PItalic -> "Italic",
      PFirstLineOnly -> "Only highlight the first line")
  }
}

object CallByNameParamCreationPreferencePage {
  val PActive = "scala.tools.eclipse.ui.preferences.callByNameParamCreation.enabled"
  val PBold = "scala.tools.eclipse.ui.preferences.callByNameParamCreation.text.bold"
  val PItalic = "scala.tools.eclipse.ui.preferences.callByNameParamCreation.text.italic"
  val PFirstLineOnly  = "scala.tools.eclipse.ui.preferences.callByNameParamCreation.firstline.only"
}

class CallByNameParamCreationPreferenceInitializer extends AbstractPreferenceInitializer {
  import CallByNameParamCreationPreferencePage._

  override def initializeDefaultPreferences() {
    val store = IScalaPlugin().getPreferenceStore
    store.setDefault(PActive, true)
    store.setDefault(PBold, false)
    store.setDefault(PItalic, false)
    store.setDefault(PFirstLineOnly, true)
  }
}
