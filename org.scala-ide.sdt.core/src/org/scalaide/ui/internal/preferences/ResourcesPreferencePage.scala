/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.dialogs.IMessageProvider
import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.IntegerFieldEditor
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Group
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.core.IScalaPlugin
import scala.util.Try

class ResourcesPreferencePage extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID) with IWorkbenchPreferencePage {
  import ResourcesPreferences._

  setPreferenceStore(IScalaPlugin().getPreferenceStore())

  // vars due to proper ordering of initialization
  private var presCompGroup: Group = null
  private var presCompInnerGroup: Composite = null

  // these ones are disposed by parent class
  private var closingEnabledEditor: BooleanFieldEditor = null
  private var maxIdlenessLengthEditor: IntegerFieldEditor = null

  override def init(wb: IWorkbench): Unit = {}

  override def createFieldEditors(): Unit = {
    presCompGroup = new Group(getFieldEditorParent, SWT.NONE)
    presCompGroup.setText("Scala Presentation Compiler")

    presCompGroup.setLayout(new GridLayout(1, true))
    presCompGroup.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false))

    closingEnabledEditor = new BooleanFieldEditor(PRES_COMP_CLOSE_UNUSED, "Close unused compiler instances", presCompGroup) {
      override def valueChanged(oldValue: Boolean, newValue: Boolean): Unit = {
        super.valueChanged(oldValue, newValue)
        updateClosingPresentationCompilerEditors(newValue)
      }
    }
    addField(closingEnabledEditor)

    // workaround to limit width of text field
    presCompInnerGroup = new Composite(presCompGroup, SWT.NONE)

    maxIdlenessLengthEditor = new IntegerFieldEditor(PRES_COMP_MAX_IDLENESS_LENGTH, "After following number of seconds of inactivity", presCompInnerGroup) {
      override def valueChanged(): Unit = {
        super.valueChanged()
        Try(getIntValue()).foreach(checkCurrentIdlenessLength)
      }
    }
    maxIdlenessLengthEditor.setValidRange(10, Integer.MAX_VALUE)
    addField(maxIdlenessLengthEditor)
  }

  override def initialize(): Unit = {
    super.initialize()
    updateClosingPresentationCompilerEditors(closingEnabledEditor.getBooleanValue())
    checkCurrentIdlenessLength(maxIdlenessLengthEditor.getIntValue())
  }

  override def performDefaults(): Unit = {
    super.performDefaults()
    updateClosingPresentationCompilerEditors(closingEnabledEditor.getBooleanValue())
    checkCurrentIdlenessLength(maxIdlenessLengthEditor.getIntValue())
  }

  override def dispose(): Unit = {
    if (presCompGroup != null) presCompGroup.dispose()
    if (presCompInnerGroup != null) presCompInnerGroup.dispose()
    super.dispose()
  }

  private def updateClosingPresentationCompilerEditors(enabled: Boolean): Unit = {
    maxIdlenessLengthEditor.setEnabled(enabled, presCompInnerGroup)
  }

  private def checkCurrentIdlenessLength(seconds: Int): Unit =
    if (seconds < 30)
      setMessage("It is recommended to close presentation compilers after at least 30 seconds.", IMessageProvider.WARNING)
    else
      setMessage(getTitle())

  // hack to send notification when all properties has been already stored
  override def performOk(): Boolean = {
    val prefStore = getPreferenceStore()
    val presCompPrefsChanged = closingEnabledEditor.getBooleanValue() != prefStore.getBoolean(PRES_COMP_CLOSE_UNUSED) ||
      maxIdlenessLengthEditor.getIntValue() != prefStore.getInt(PRES_COMP_MAX_IDLENESS_LENGTH)

    val result = super.performOk()

    if (presCompPrefsChanged) { // we want to notify listeners - all new values are already stored
      val newChangeMarkerValue = !prefStore.getBoolean(PRES_COMP_PREFERENCES_CHANGE_MARKER)
      prefStore.setValue(PRES_COMP_PREFERENCES_CHANGE_MARKER, newChangeMarkerValue)
    }
    result
  }

}

object ResourcesPreferences {

  val PRES_COMP_CLOSE_UNUSED = "org.scala-ide.sdt.core.resources.presentationCompiler.closeUnused"
  val PRES_COMP_MAX_IDLENESS_LENGTH = "org.scala-ide.sdt.core.resources.presentationCompiler.maxIdlenessLength"

  /**
   * Changes in preferences related to closing presentation compilers should be always taken into account together.
   * Unfortunately preferences are saved separately step by step and notification about the change of one of them is sent before new values
   * of another ones are stored in preference store. That's why we need some hack - to send one notification when new values of all
   * related preferences are stored.
   */
  val PRES_COMP_PREFERENCES_CHANGE_MARKER = "org.scala-ide.sdt.core.resources.presentationCompiler.preferencesChangeMarker"
}

class ResourcesPreferencePageInitializer extends AbstractPreferenceInitializer {
  import ResourcesPreferences._

  override def initializeDefaultPreferences(): Unit = {
    val store = IScalaPlugin().getPreferenceStore()
    store.setDefault(PRES_COMP_CLOSE_UNUSED, true)
    store.setDefault(PRES_COMP_MAX_IDLENESS_LENGTH, 120)
    store.setDefault(PRES_COMP_PREFERENCES_CHANGE_MARKER, true)
  }
}
