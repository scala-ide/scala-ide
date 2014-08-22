package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Remove

object RemoveCurlyBracePairSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[RemoveCurlyBracePair],
  name = "Remove pairs of curly braces",
  description =
    "In case the opening curly brace is removed, this auto edit also" +
    " removes the closing brace if it follows the opening curly brace."
)

trait RemoveCurlyBracePair extends AutoEdit {

  override def setting = RemoveCurlyBracePairSetting

  override def perform() = {
    rule(textChange) {
      case Remove(start, end) if start+1 == end =>
        subrule(document.textRangeOpt(start, end+1)) {
          case Some("{}") => Remove(start, end+1) withCursorPos start
        }
    }
  }
}
