package org.scalaide.refactoring.internal

import scala.tools.refactoring.Refactoring
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.ui.internal.preferences.EditorPreferencePage
import org.scalaide.core.internal.formatter.FormatterPreferences._
import scalariform.formatter.preferences._

/**
 * Enables passing the user's source formatting preferences to the refactoring library's
 * source code generation.
 */
trait UserPreferencesFormatting {
  this: ScalaIdeRefactoring =>

  /**
   * Refactoring actions should mix in this trait when creating a refactoring instance to
   * automatically pass the user's formatting preferences to the refactoring implementation.
   */
  trait FormattingOverrides {
    this: Refactoring =>

    override val defaultIndentationStep: String = {
      val p = ScalaPlugin().getPreferenceStore
      val indentWithTabs = p.getBoolean(IndentWithTabs.eclipseKey)
      val spaces = p.getInt(IndentSpaces.eclipseKey)

      if (indentWithTabs) "\t" else " "*spaces
    }

    override val spacingAroundMultipleImports: String = {
      val addSpaces = ScalaPlugin().getPreferenceStore.getBoolean(EditorPreferencePage.SPACES_AROUND_IMPORT_BLOCKS)
      if (addSpaces) " " else ""
    }
  }
}
