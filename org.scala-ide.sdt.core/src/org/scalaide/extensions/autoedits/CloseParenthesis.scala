package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.TextChange

object CloseParenthesisSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[CloseParenthesis],
  name = "Close (parentheses)",
  description =
    "Closes a typed opening parenthesis if necessary. For a more detailed" +
    " explanation when the parenthesis is not closed, see the description of the" +
    " auto edit about closing curly braces."
)

trait CloseParenthesis extends CloseMatchingPair {

  override def opening = '('
  override def closing = ')'

  override def setting = CloseParenthesisSetting

  override def perform() = {
    check(textChange) {
      case TextChange(start, end, "(") =>
        if (autoClosingRequired(end))
          Some(TextChange(start, end, "()") withLinkedModel(start+2, singleLinkedPos(start+1)))
        else
          None
    }
  }
}
