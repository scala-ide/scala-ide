package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add
import org.scalaide.core.text.Remove

object CloseCurlyBraceSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[CloseCurlyBrace],
  name = "Automatically close curly brace",
  description =
    "Closes a typed opening brace if necessary. Most of the time the closing" +
    " brace is added when an opening brace is typed but for some cases the IDE" +
    " can detect that the closing brace is not necessary and therefore it leaves" +
    " it out. This may be the case if there are unmatched pairs of braces in the" +
    " current line. If the cursor is positioned directly before non white space," +
    " the curly brace is never closed."
)

trait CloseCurlyBrace extends AutoEdit {

  override def setting = CloseCurlyBraceSetting

  override def perform() = {
    rule(textChange) {
      case Add(start, "{") =>
        if (autoClosingRequired(start))
          Some(Add(start, "{}") withLinkedModel (start+2, singleLinkedPos(start+1)))
        else
          None
    }
  }

  def singleLinkedPos(pos: Int): Seq[Seq[(Int, Int)]] =
    Seq(Seq((pos, 0)))

  /**
   * Checks if it is necessary to insert a closing brace. Normally this is
   * always the case with two exceptions:
   *
   * 1. The caret is positioned directly before non white space
   * 2. There are unmatched closing braces after the caret position.
   */
  def autoClosingRequired(offset: Int): Boolean = {
    val lineInfo = document.lineInformationOfOffset(offset)
    val lineAfterCaret = document.textRange(offset, lineInfo.end).toSeq

    if (lineAfterCaret.isEmpty) true
    else {
      val lineComplete = lineInfo.text.toSeq
      val lineBeforeCaret = lineComplete.take(lineComplete.length - lineAfterCaret.length)

      val bracesTotal = lineComplete.count(_ == '}') - lineComplete.count(_ == '{')
      val bracesStart = lineComplete.takeWhile(_ != '{').count(_ == '}')
      val bracesEnd = lineComplete.reverse.takeWhile(_ != '}').count(_ == '{')
      val blacesRelevant = bracesTotal - bracesStart - bracesEnd

      val hasClosingBracket = lineAfterCaret.contains('}') && !lineAfterCaret.takeWhile(_ == '}').contains('{')
      val hasOpeningBracket = lineBeforeCaret.contains('{') && !lineBeforeCaret.reverse.takeWhile(_ == '{').contains('}')

      if (hasOpeningBracket && hasClosingBracket)
        blacesRelevant <= 0
      else
        Character.isWhitespace(lineAfterCaret(0))
    }
  }
}
