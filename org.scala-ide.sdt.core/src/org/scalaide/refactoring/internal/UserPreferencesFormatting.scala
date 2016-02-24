package org.scalaide.refactoring.internal

import scala.tools.refactoring.Refactoring
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.ui.internal.preferences.EditorPreferencePage

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

    override val spacingAroundMultipleImports: String = {
      val addSpaces = ScalaPlugin().getPreferenceStore.getBoolean(EditorPreferencePage.SPACES_AROUND_IMPORT_BLOCKS)
      if (addSpaces) " " else ""
    }
  }
}
