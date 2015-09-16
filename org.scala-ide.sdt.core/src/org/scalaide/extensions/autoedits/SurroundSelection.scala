package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.TextChange

object SurroundSelectionWithStringSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[SurroundSelectionWithString],
  name = "Surround selection with \"quotes\"",
  description = "Automatically surrounds a selection with quotes when a quotation mark is typed."
)

object SurroundSelectionWithParenthesesSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[SurroundSelectionWithParentheses],
  name = "Surround selection with (parentheses)",
  description = "Automatically surrounds a selection with parentheses when an opening parenthesis is typed."
)

object SurroundSelectionWithBracesSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[SurroundSelectionWithBraces],
  name = "Surround selection with {braces}",
  description = "Automatically surrounds a selection with braces when an opening brace is typed."
)

object SurroundSelectionWithBracketsSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[SurroundSelectionWithBrackets],
  name = "Surround selection with [square] brackets",
  description = "Automatically surrounds a selection with square brackets when an opening square bracket is typed."
)

object SurroundSelectionWithAngleBracketsSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[SurroundSelectionWithAngleBrackets],
  name = "Surround selection with <angle> brackets",
  description = "Automatically surrounds a selection with angle brackets when an opening angle bracket is typed."
)

trait SurroundSelection extends AutoEdit {

  def opening: String
  def closing: String

  override def perform() = {
    check(textChange) {
      case c @ TextChange(start, end, o) if end > start && o == opening ⇒
        Some(TextChange(start, end, opening + document.textRange(start, end) + closing))
    }
  }
}

trait SurroundSelectionWithString extends SurroundSelection {
  override def opening = "\""
  override def closing = "\""
  override def setting = SurroundSelectionWithStringSetting
}

trait SurroundSelectionWithParentheses extends SurroundSelection {
  override def opening = "("
  override def closing = ")"
  override def setting = SurroundSelectionWithParenthesesSetting
}

trait SurroundSelectionWithBraces extends SurroundSelection {
  override def opening = "{"
  override def closing = "}"
  override def setting = SurroundSelectionWithBracesSetting
}

trait SurroundSelectionWithBrackets extends SurroundSelection {
  override def opening = "["
  override def closing = "]"
  override def setting = SurroundSelectionWithBracketsSetting
}

trait SurroundSelectionWithAngleBrackets extends SurroundSelection {
  override def opening = "<"
  override def closing = ">"
  override def setting = SurroundSelectionWithAngleBracketsSetting
}
