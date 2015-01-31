package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add

object CloseAngleBracketSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[CloseAngleBracket],
  name = "Close <angle> brackets",
  description =
    "Closes a typed opening angle bracket if necessary. For a more detailed" +
    " explanation when the bracket is not closed, see the description of the" +
    " auto edit about closing curly braces."
)

trait CloseAngleBracket extends CloseMatchingPair {

  override def opening = '<'
  override def closing = '>'

  override def setting = CloseBracketSetting

  override def perform() = {
    check(textChange) {
      case Add(start, "<") =>
        if (autoClosingRequired(start))
          Some(Add(start, "<>") withLinkedModel(start+2, singleLinkedPos(start+1)))
        else
          None
    }
  }
}
