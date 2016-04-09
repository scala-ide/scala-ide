package org.scalaide.ui.internal.preferences

import java.io.File

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.IntegerFieldEditor
import org.eclipse.jface.preference.StringFieldEditor
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.ScalaIdeDataStore

object ScalaPreferences {

  val ReconcilerDelayId = "org.scalaide.core.reconcilerDelay"
}

class ScalaPreferences extends FieldEditorPreferencePage with IWorkbenchPreferencePage {

  private var dataStoreField: StringFieldEditor = _
  private var reconcilerField: IntegerFieldEditor = _

  override def createFieldEditors(): Unit = {
    dataStoreField = new StringFieldEditor(ScalaIdeDataStore.DataStoreId, "Path to data store: ", getFieldEditorParent) {
      override def checkState() = {
        val f = new File(getTextControl.getText)
        val isValid = f.exists() && f.isDirectory()
        if (isValid)
          clearErrorMessage()
        else
          showErrorMessage("Path must point to a directory")
        isValid
      }
    }
    addField(dataStoreField)

    reconcilerField = new IntegerFieldEditor(ScalaPreferences.ReconcilerDelayId, "Reconciler delay (ms): ", getFieldEditorParent)
    addField(reconcilerField)
  }

  override def initialize(): Unit = {
    super.initialize()
    dataStoreField.setStringValue(ScalaIdeDataStore.dataStoreLocation)
    reconcilerField.setStringValue(IScalaPlugin().getPreferenceStore.getInt(ScalaPreferences.ReconcilerDelayId).toString)
  }

  override def init(workbench: IWorkbench): Unit = ()

  override def performOk() = {
    super.performOk()
    val store = IScalaPlugin().getPreferenceStore
    store.setValue(ScalaIdeDataStore.DataStoreId, dataStoreField.getStringValue)
    store.setValue(ScalaPreferences.ReconcilerDelayId, reconcilerField.getStringValue)
    true
  }
}

class ScalaPreferenceInitializer extends AbstractPreferenceInitializer {

  override def initializeDefaultPreferences(): Unit = {
    val store = IScalaPlugin().getPreferenceStore
    store.setDefault(ScalaIdeDataStore.DataStoreId, ScalaIdeDataStore.DefaultDataStoreLocation)
    store.setDefault(ScalaPreferences.ReconcilerDelayId, 500)
  }

}
