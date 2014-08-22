package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add

object CloseBracketSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[CloseBracket],
  name = "Close [square] brackets",
  description =
    "Closes a typed opening square bracket if necessary. For a more detailed" +
    " explanation when the bracket is not closed, see the description of the" +
    " auto edit about closing curly braces."
)

trait CloseBracket extends CloseMatchingPair {

  override def opening = '['
  override def closing = ']'

  override def setting = CloseBracketSetting

  override def perform() = {
    rule(textChange) {
      case Add(start, "[") =>
        if (declaresProbablyGenerics(start) || autoClosingRequired(start))
          Some(Add(start, "[]") withLinkedModel(start+2, singleLinkedPos(start+1)))
        else
          None
    }
  }

  /**
   * Returns `true` if the opening `[` is placed in a position where it is meant
   * to declare generics, i.e. after a class or def declaration.
   */
  private def declaresProbablyGenerics(caret: Int): Boolean = {
    val lineInfo = document.lineInformationOfOffset(caret)
    val lineBeforeCaret = document.textRange(lineInfo.start, caret)
    lineBeforeCaret matches """.*?(class|def) [^\(]+?"""
  }
}
