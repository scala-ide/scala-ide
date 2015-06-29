package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add

object JumpOverClosingAngleBracketSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[JumpOverClosingAngleBracket],
  name = "Jump over closing <angle> brackets",
  description =
    "If a bracket is typed but at the cursor position such a closing" +
    " bracket already exists, the cursor jumps over this bracket without adding" +
    " another one."
)

trait JumpOverClosingAngleBracket extends AutoEdit {

  override def setting = JumpOverClosingAngleBracketSetting

  override def perform() = {
    check(textChange) {
      case Add(start, ">") =>
        lookupChar(0) {
          case '>' =>
            Add(start, "") withCursorPos start+1
        }
    }
  }
}
