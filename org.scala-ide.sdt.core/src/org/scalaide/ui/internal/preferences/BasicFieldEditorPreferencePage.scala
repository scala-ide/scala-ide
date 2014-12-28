package org.scalaide.ui.internal.preferences

import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.IWorkbench
import org.scalaide.core.IScalaPlugin
import org.eclipse.jface.preference.BooleanFieldEditor

abstract class BasicFieldEditorPreferencePage(description: String) extends FieldEditorPreferencePage with IWorkbenchPreferencePage {
  setPreferenceStore(IScalaPlugin().getPreferenceStore)
  setDescription(description)

  override def init(workbench: IWorkbench) = Unit

  protected def addBooleanFieldEditors(editors: (String, String)*) {
    for ((name, label) <- editors) {
      addField(new BooleanFieldEditor(name, label, getFieldEditorParent))
    }
  }
}
