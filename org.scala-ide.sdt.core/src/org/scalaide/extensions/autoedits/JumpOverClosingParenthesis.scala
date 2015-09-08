package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add

object JumpOverClosingParenthesisSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[JumpOverClosingParenthesis],
  name = "Jump over closing (parenthesis)",
  description =
    "If a parenthesis is typed but at the cursor position such a closing" +
    " parenthesis already exists, the cursor jumps over this parenthesis without adding" +
    " another one."
)

trait JumpOverClosingParenthesis extends AutoEdit {

  override def setting = JumpOverClosingParenthesisSetting

  override def perform() = {
    check(textChange) {
      case Add(start, ")") =>
        lookupChar(0) {
          case ')' =>
            Add(start, "") withCursorPos start+1
        }
    }
  }
}
