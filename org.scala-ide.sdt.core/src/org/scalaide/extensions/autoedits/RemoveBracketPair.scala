package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Remove

object RemoveBracketPairSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[RemoveBracketPair],
  name = "Remove pairs of [square] brackets",
  description =
    "In case the opening bracket is removed, this auto edit also" +
    " removes the closing bracket if it follows the opening bracket."
)

trait RemoveBracketPair extends AutoEdit {
  override def setting = RemoveBracketPairSetting

  override def perform() = {
    check(textChange) {
      case Remove(start, end) if start+1 == end =>
        subcheck(document.textRangeOpt(start, end+1)) {
          case Some("[]") => Remove(start, end+1) withCursorPos start
        }
    }
  }
}
