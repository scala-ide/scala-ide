package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.TextChange

object CloseCurlyBraceSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[CloseCurlyBrace],
  name = "Close {curly} braces",
  description =
    "Closes a typed opening brace if necessary. Most of the time the closing" +
    " brace is added when an opening brace is typed but for some cases the IDE" +
    " can detect that the closing brace is not necessary and therefore it leaves" +
    " it out. This may be the case if there are unmatched pairs of braces in the" +
    " current line. If the cursor is positioned directly before non white space," +
    " the curly brace is never closed unless it is nested in another pair of matching elements."
)

trait CloseCurlyBrace extends CloseMatchingPair {

  override def opening = '{'
  override def closing = '}'

  override def setting = CloseCurlyBraceSetting

  override def perform() = {
    check(textChange) {
      case TextChange(start, end, "{") =>
        if (autoClosingRequired(end))
          Some(TextChange(start, end, "{}") withLinkedModel (start+2, singleLinkedPos(start+1)))
        else
          None
    }
  }
}
