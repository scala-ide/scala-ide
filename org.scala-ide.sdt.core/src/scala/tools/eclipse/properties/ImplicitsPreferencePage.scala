package scala.tools.eclipse
package properties

import org.eclipse.jface.preference._
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.IWorkbench

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.IPreferenceStore

import scala.tools.eclipse.ScalaPlugin

class ImplicitsPreferencePage extends FieldEditorPreferencePage with IWorkbenchPreferencePage {
  import ImplicitsPreferencePage._

  setPreferenceStore(ScalaPlugin.plugin.getPreferenceStore)
  setDescription("""
	    Set the highlighting for implicit conversions and implicit parameters.
	    See also General > Editors > Text Editors > Annotations (Scala Implicit)
  """)

  override def createFieldEditors() {
    addField(new BooleanFieldEditor(P_ACTIVE, "Active", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_BOLD, "Bold", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ITALIC, "Italic", getFieldEditorParent))
  }

  def init(workbench: IWorkbench) {}

}

object ImplicitsPreferencePage {
  val BASE = "scala.tools.eclipse.ui.preferences.implicit."
  val P_ACTIVE = BASE + "enabled"
  val P_BOLD = BASE + "text.bold"
  val P_ITALIC = BASE + "text.italic"
}

class ImplicitsPagePreferenceInitializer extends AbstractPreferenceInitializer {

  import ImplicitsPreferencePage._

  override def initializeDefaultPreferences() {
    val store = ScalaPlugin.plugin.getPreferenceStore
    store.setDefault(P_ACTIVE, false)
    store.setDefault(P_BOLD, false)
    store.setDefault(P_ITALIC, false)
  }

}