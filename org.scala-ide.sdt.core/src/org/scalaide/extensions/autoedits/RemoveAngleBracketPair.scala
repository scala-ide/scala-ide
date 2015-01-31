package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Remove

object RemoveAngleBracketPairSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[RemoveAngleBracketPair],
  name = "Remove pairs of <angle> brackets",
  description =
    "In case the opening angle bracket is removed, this auto edit also" +
    " removes the closing angle bracket if it follows the opening angle bracket."
)

trait RemoveAngleBracketPair extends AutoEdit {
  override def setting = RemoveAngleBracketPairSetting

  override def perform() = {
    check(textChange) {
      case Remove(start, end) if start+1 == end =>
        subcheck(document.textRangeOpt(start, end+1)) {
          case Some("<>") => Remove(start, end+1) withCursorPos start
        }
    }
  }

}
