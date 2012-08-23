package scala.tools.eclipse
package properties

import org.eclipse.jface.preference._
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.IWorkbench
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.IPreferenceStore
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.swt.widgets.Link
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.jdt.internal.ui.preferences.PreferencesMessages
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.ui.dialogs.PreferencesUtil

class ImplicitsPreferencePage extends FieldEditorPreferencePage with IWorkbenchPreferencePage {
  import ImplicitsPreferencePage._
  import util.SWTUtils._

  setPreferenceStore(ScalaPlugin.plugin.getPreferenceStore)
  setDescription("""
Set the highlighting for implicit conversions and implicit parameters.
  """)

  override def createContents(parent: Composite): Control = {
    val control = super.createContents(parent).asInstanceOf[Composite]
    val link = new Link(control, SWT.NONE)
    link.setText("""More options for highlighting for implicit conversions on the <a href="org.eclipse.ui.editors.preferencePages.Annotations">Text Editors/Annotations</a> preference page.""")
    link.addSelectionListener { e: SelectionEvent =>
      PreferencesUtil.createPreferenceDialogOn(parent.getShell, e.text, null, null)
    }

    control
  }

  override def createFieldEditors() {
    addField(new BooleanFieldEditor(P_ACTIVE, "Enabled", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_BOLD, "Bold", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ITALIC, "Italic", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_CONVERSIONS_ONLY, "Only highlight implicit conversions", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_FIRST_LINE_ONLY, "Only highlight the first line in an implicit conversion", getFieldEditorParent))
  }

  def init(workbench: IWorkbench) {}

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
    val store = ScalaPlugin.plugin.getPreferenceStore
    store.setDefault(P_ACTIVE, true)
    store.setDefault(P_BOLD, false)
    store.setDefault(P_ITALIC, false)
    store.setDefault(P_CONVERSIONS_ONLY, true)
    store.setDefault(P_FIRST_LINE_ONLY, true)
  }
}
