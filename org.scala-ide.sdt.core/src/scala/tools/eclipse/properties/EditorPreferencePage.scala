package scala.tools.eclipse
package properties

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.IWorkbench
import EditorPreferencePage._
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.layout.GridData

class EditorPreferencePage extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID) with IWorkbenchPreferencePage {

  setPreferenceStore(ScalaPlugin.plugin.getPreferenceStore)

  override def createFieldEditors() {
    addField(new BooleanFieldEditor(P_ENABLE_SMART_BRACKETS, "Automatically surround selection with [brackets]", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ENABLE_SMART_BRACES, "Automatically surround selection with {braces}", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ENABLE_SMART_PARENS, "Automatically surround selection with (parenthesis)", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ENABLE_SMART_QUOTES, "Automatically surround selection with \"quotes\"", getFieldEditorParent))

    new Label(getFieldEditorParent, SWT.SEPARATOR | SWT.HORIZONTAL).setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
    addField(new BooleanFieldEditor(P_ENABLE_AUTO_CLOSING_BRACES, "Enable auto closing braces when editing an existing line", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ENABLE_AUTO_CLOSING_COMMENTS, "Automatically close multi line comments and Scaladoc", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ENABLE_AUTO_ESCAPE_LITERALS, "Automatically escape \" signs in string literals", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ENABLE_AUTO_ESCAPE_SIGN, "Automatically escape \\ signs in string and character literals", getFieldEditorParent))
    addField(new BooleanFieldEditor(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, "Automatically remove complete escaped sign in\nstring and character literals", getFieldEditorParent))

    new Label(getFieldEditorParent, SWT.SEPARATOR | SWT.HORIZONTAL).setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
    addField(new BooleanFieldEditor(P_ENABLE_MARK_OCCURRENCES, "Mark Occurences of the selected element in the current file", getFieldEditorParent))
  }

  def init(workbench: IWorkbench) {}

}

object EditorPreferencePage {
  private final val BASE = "scala.tools.eclipse.editor."

  final val P_ENABLE_SMART_BRACKETS = BASE + "smartBrackets"
  final val P_ENABLE_SMART_BRACES = BASE + "smartBraces"
  final val P_ENABLE_SMART_PARENS = BASE + "smartParens"
  final val P_ENABLE_SMART_QUOTES = BASE + "smartQuotes"

  final val P_ENABLE_AUTO_CLOSING_BRACES = BASE + "autoClosingBrace"
  final val P_ENABLE_AUTO_CLOSING_COMMENTS = BASE + "autoClosingComments"
  final val P_ENABLE_AUTO_ESCAPE_LITERALS = BASE + "autoEscapeLiterals"
  final val P_ENABLE_AUTO_ESCAPE_SIGN = BASE + "autoEscapeSign"
  final val P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN = BASE + "autoRemoveEscapedSign"

  final val P_ENABLE_MARK_OCCURRENCES = BASE + "markOccurences"
}

class EditorPreferenceInitializer extends AbstractPreferenceInitializer {

  override def initializeDefaultPreferences() {
    val store = ScalaPlugin.plugin.getPreferenceStore
    store.setDefault(P_ENABLE_SMART_BRACKETS, false)
    store.setDefault(P_ENABLE_SMART_BRACES, false)
    store.setDefault(P_ENABLE_SMART_PARENS, false)
    store.setDefault(P_ENABLE_SMART_QUOTES, false)

    store.setDefault(P_ENABLE_AUTO_CLOSING_BRACES, true)
    store.setDefault(P_ENABLE_AUTO_CLOSING_COMMENTS, true)
    store.setDefault(P_ENABLE_AUTO_ESCAPE_LITERALS, false)
    store.setDefault(P_ENABLE_AUTO_ESCAPE_SIGN, false)
    store.setDefault(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)

    store.setDefault(P_ENABLE_MARK_OCCURRENCES, false)
  }
}