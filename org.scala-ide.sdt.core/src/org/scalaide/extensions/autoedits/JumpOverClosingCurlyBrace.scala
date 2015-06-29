package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add

object JumpOverClosingCurlyBraceSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[JumpOverClosingCurlyBrace],
  name = "Jump over closing {curly} braces",
  description =
    "If a closing curly brace is typed but at the cursor position such a closing" +
    " curly brace already exists, the cursor jumps over this brace without adding" +
    " another one."
)

trait JumpOverClosingCurlyBrace extends AutoEdit {

  override def setting = JumpOverClosingCurlyBraceSetting

  override def perform() = {
    check(textChange) {
      case Add(start, "}") =>
        lookupChar(0) {
          case '}' =>
            Add(start, "") withCursorPos start+1
        }
    }
  }
}
