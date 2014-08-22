package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Remove

object RemoveParenthesisPairSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[RemoveParenthesisPair],
  name = "Remove pairs of parentheses",
  description =
    "In case the opening parenthesis is removed, this auto edit also" +
    " removes the closing parenthesis if it follows the opening parenthesis."
)

trait RemoveParenthesisPair extends AutoEdit {
  override def setting = RemoveCurlyBracePairSetting

  override def perform() = {
    rule(textChange) {
      case Remove(start, end) if start+1 == end =>
        subrule(document.textRangeOpt(start, end+1)) {
          case Some("()") => Remove(start, end+1) withCursorPos start
        }
    }
  }
}
