package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add

object JumpOverClosingBracketSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[JumpOverClosingBracket],
  name = "Jump over closing [square] brackets",
  description =
    "If a bracket is typed but at the cursor position such a closing" +
    " bracket already exists, the cursor jumps over this bracket without adding" +
    " another one."
)

trait JumpOverClosingBracket extends AutoEdit {

  override def setting = JumpOverClosingBracketSetting

  override def perform() = {
    check(textChange) {
      case Add(start, "]") =>
        lookupChar(0) {
          case ']' =>
            Add(start, "") withCursorPos start+1
        }
    }
  }
}
