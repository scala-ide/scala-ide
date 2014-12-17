package org.scalaide.ui.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.swt.widgets.Link
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.ui.dialogs.PreferencesUtil
import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.ui.IWorkbench
import org.scalaide.core.internal.ScalaPlugin


class MacrosPreferencePage extends FieldEditorPreferencePage with IWorkbenchPreferencePage {
  import MacrosPreferencePage._
  import org.scalaide.util.eclipse.SWTUtils.fnToSelectionAdapter

  setPreferenceStore(ScalaPlugin().getPreferenceStore)

  override def createContents(parent: Composite): Control = {
    val control = super.createContents(parent).asInstanceOf[Composite]
    val link = new Link(control, SWT.NONE)
    link.setText("""More options for highlighting the macro expansions on the <a href="org.eclipse.ui.editors.preferencePages.Annotations">Text Editors/Annotations</a> preference page.""")
    link.addSelectionListener { e: SelectionEvent =>
      PreferencesUtil.createPreferenceDialogOn(parent.getShell, e.text, null, null)
    }

    control
  }

  override def createFieldEditors() {
    addField(new BooleanFieldEditor(P_ACTIVE, "Enable macro expand functionality", getFieldEditorParent))
  }

  def init(workbench: IWorkbench) {}

}

object MacrosPreferencePage {
  val BASE = "scala.tools.eclipse.ui.preferences.macro."
  val P_ACTIVE = BASE + "enabled"
}


class MacrosPagePreferenceInitializer extends AbstractPreferenceInitializer {
  import MacrosPreferencePage._

  override def initializeDefaultPreferences() {
    val store = ScalaPlugin().getPreferenceStore
    store.setDefault(P_ACTIVE, true)
  }
}