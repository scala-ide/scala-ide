package scala.tools.eclipse
package properties

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.{ FieldEditorPreferencePage, BooleanFieldEditor }
import org.eclipse.ui.{ IWorkbenchPreferencePage, IWorkbench }
import EditorPreferencePage._

class EditorPreferencePage extends FieldEditorPreferencePage with IWorkbenchPreferencePage {

  setPreferenceStore(ScalaPlugin.plugin.getPreferenceStore)

  override def createFieldEditors() {
    addField(new BooleanFieldEditor(P_ENABLE_SMART_BRACKETS, "Automatically surround selection with [brackets]", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ENABLE_SMART_BRACES, "Automatically surround selection with {braces}", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ENABLE_SMART_PARENS, "Automatically surround selection with (parenthesis)", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ENABLE_SMART_QUOTES, "Automatically surround selection with \"quotes\"", getFieldEditorParent))

    addField(new BooleanFieldEditor(P_ENABLE_AUTO_CLOSING_BRACES, "Enable auto closing braces when editing an existing line", getFieldEditorParent))
  }

  def init(workbench: IWorkbench) {}

}

object EditorPreferencePage {
  final val BASE = "scala.tools.eclipse.editor."
  final val P_ENABLE_SMART_BRACKETS = BASE + "smartBrackets"
  final val P_ENABLE_SMART_BRACES = BASE + "smartBraces"
  final val P_ENABLE_SMART_PARENS = BASE + "smartParens"
  final val P_ENABLE_SMART_QUOTES = BASE + "smartQuotes"

  final val P_ENABLE_AUTO_CLOSING_BRACES = BASE + "autoClosingBrace"
}

class DebugPreferenceInitializer extends AbstractPreferenceInitializer {

  override def initializeDefaultPreferences() {
    val store = ScalaPlugin.plugin.getPreferenceStore
    store.setDefault(P_ENABLE_SMART_BRACKETS, false)
    store.setDefault(P_ENABLE_SMART_BRACES, false)
    store.setDefault(P_ENABLE_SMART_PARENS, false)
    store.setDefault(P_ENABLE_SMART_QUOTES, false)

    store.setDefault(P_ENABLE_AUTO_CLOSING_BRACES, true)
  }
}