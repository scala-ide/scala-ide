package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add
import org.scalaide.core.text.Replace
import org.scalaide.util.eclipse.RegionUtils._

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

trait SurroundSelection extends AutoEdit {

  def opening: String
  def closing: String

  def perform() = {
    check(textChange) {
      case Add(start, o) if o == opening ⇒
        subcheck(textSelection) {
          case sel if sel.length > 0 ⇒
            Replace(sel.start, sel.end, opening + sel.getText + closing)
        }
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
