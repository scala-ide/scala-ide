package org.scalaide.ui.internal.editor

import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.ui.PreferenceConstants
import org.scalaide.core.internal.formatter.FormatterPreferences

import scalariform.formatter.preferences.IndentSpaces
import scalariform.formatter.preferences.IndentWithTabs

class JdtPreferenceProvider(val project: IJavaProject) extends PreferenceProvider {
  private def preferenceStore = JavaPlugin.getDefault().getCombinedPreferenceStore()

  def updateCache(): Unit = {
    put(PreferenceConstants.EDITOR_CLOSE_BRACES,
      preferenceStore.getBoolean(PreferenceConstants.EDITOR_CLOSE_BRACES).toString)
    put(PreferenceConstants.EDITOR_SMART_TAB,
      preferenceStore.getBoolean(PreferenceConstants.EDITOR_SMART_TAB).toString)

    val formatterPreferences = FormatterPreferences.getPreferences(project)
    val indentWithTabs = formatterPreferences(IndentWithTabs).toString
    val indentSpaces = formatterPreferences(IndentSpaces).toString

    put(ScalaIndenter.TAB_SIZE, indentSpaces)
    put(ScalaIndenter.INDENT_SIZE, indentSpaces)
    put(ScalaIndenter.INDENT_WITH_TABS, indentWithTabs)

    def populateFromProject(key: String) = {
      put(key, project.getOption(key, true))
    }

    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_EXPRESSIONS_IN_ARRAY_INITIALIZER)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_CONDITIONAL_EXPRESSION)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_SWITCH)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_CASES)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_PARAMETERS_IN_METHOD_DECLARATION)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_ARGUMENTS_IN_METHOD_INVOCATION)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_INDENT_STATEMENTS_COMPARE_TO_BLOCK)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_INDENT_STATEMENTS_COMPARE_TO_BODY)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_INDENT_BODY_DECLARATIONS_COMPARE_TO_TYPE_HEADER)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_ARRAY_INITIALIZER)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_METHOD_DECLARATION)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_TYPE_DECLARATION)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_CONTINUATION_INDENTATION)
  }
}
