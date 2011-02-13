package scala.tools.eclipse.formatter

import org.eclipse.ui._
import org.eclipse.jface.preference._
import scalariform.formatter._
import scalariform.formatter.preferences._
import scala.tools.eclipse.ScalaPlugin

class FormatterPreferencePage extends FieldEditorPreferencePage with IWorkbenchPreferencePage {
  
  import FormatterPreferencePage._
  
  setPreferenceStore(ScalaPlugin.plugin.getPreferenceStore)

  def init(workbench: IWorkbench) {}

  override def createFieldEditors() {

    for (preference <- AllPreferences.preferences) {
      val preferenceType = preference.preferenceType
      preferenceType match {
        case BooleanPreference =>
          val field = new BooleanFieldEditor(prefix + preference.key, preference.description, org.eclipse.swt.SWT.NONE, getFieldEditorParent())
          addField(field)
        case IntegerPreference(min, max) =>
          val field = new IntegerFieldEditor(prefix + preference.key, preference.description, getFieldEditorParent())
          field.setValidRange(min, max)
          addField(field)
      }
    }
  }
}

object FormatterPreferencePage {

  val prefix = "scala.tools.eclipse.formatter."

  def getPreferences() = {
    val preferenceStore = ScalaPlugin.plugin.getPreferenceStore
    var preferences: IFormattingPreferences = FormattingPreferences()

    for (preference <- AllPreferences.preferences) {
      preference.preferenceType match {
        case prefType@BooleanPreference =>
          preferences = preferences.setPreference(prefType.cast(preference), preferenceStore.getBoolean(prefix + preference.key))
        case prefType@IntegerPreference(_, _) =>
          preferences = preferences.setPreference(prefType.cast(preference), preferenceStore.getInt(prefix + preference.key))
      }
    }
    preferences
  }

}
