package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add

object CloseParenthesisSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[CloseParenthesis],
  name = "Automatically close parenthesis",
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
    rule(textChange) {
      case Add(start, "(") =>
        if (autoClosingRequired(start))
          Some(Add(start, "()") withLinkedModel(start+2, singleLinkedPos(start+1)))
        else
          None
    }
  }
}
