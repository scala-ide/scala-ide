/*
 * Copyright 2012 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring

import scala.tools.eclipse.formatter.FormatterPreferences
import scala.tools.refactoring.Refactoring

import scalariform.formatter.preferences.SpaceInsideParentheses

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
      for {
        javaProject <- Option(file.getJavaProject)
        val prefs = FormatterPreferences.getPreferences(javaProject)
        if  prefs(SpaceInsideParentheses)
      } yield " "
    } getOrElse ""

    // TODO: Create more overrides here and in the refactoring library.
  }
}
