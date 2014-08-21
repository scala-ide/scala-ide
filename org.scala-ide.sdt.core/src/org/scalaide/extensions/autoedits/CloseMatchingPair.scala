package org.scalaide.extensions
package autoedits

/** Functionality for all auto edits that should handle matching pairs. */
trait CloseMatchingPair extends AutoEdit {

  def opening: Char
  def closing: Char

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

      val bracesTotal = lineComplete.count(_ == closing) - lineComplete.count(_ == opening)
      val bracesStart = lineComplete.takeWhile(_ != opening).count(_ == closing)
      val bracesEnd = lineComplete.reverse.takeWhile(_ != closing).count(_ == opening)
      val blacesRelevant = bracesTotal - bracesStart - bracesEnd

      val hasClosingBracket = lineAfterCaret.contains(closing) && !lineAfterCaret.takeWhile(_ == closing).contains(opening)
      val hasOpeningBracket = lineBeforeCaret.contains(opening) && !lineBeforeCaret.reverse.takeWhile(_ == opening).contains(closing)

      if (hasOpeningBracket && hasClosingBracket)
        blacesRelevant <= 0
      else
        Character.isWhitespace(lineAfterCaret(0))
    }
  }
}
