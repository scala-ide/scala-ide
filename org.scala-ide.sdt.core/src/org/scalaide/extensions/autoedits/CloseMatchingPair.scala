package org.scalaide.extensions
package autoedits

/** Functionality for all auto edits that should handle matching pairs. */
trait CloseMatchingPair extends AutoEdit {

  def opening: Char
  def closing: Char

  def singleLinkedPos(pos: Int): Seq[Seq[(Int, Int)]] =
    Seq(Seq((pos, 0)))

  /**
   * Checks if it is necessary to insert a closing element. Normally this is
   * always the case with these exceptions:
   *
   * 1. The caret is positioned directly before non white space
   * 2. There are unmatched closing elements after the caret position
   * 3. The caret is located in a pair of matching elements
   */
  def autoClosingRequired(offset: Int): Boolean = {
    val lineInfo = document.lineInformationOfOffset(offset)
    val lineAfterCaret = document.textRange(offset, lineInfo.end).toSeq

    if (lineAfterCaret.isEmpty) true
    else {
      val line = lineInfo.text.toSeq
      val lineBeforeCaret = document.textRange(lineInfo.start, offset).toSeq

      val total = line.count(_ == closing) - line.count(_ == opening)
      val closingBeforeFirstOpening = line.takeWhile(_ != opening).count(_ == closing)
      val openingAfterLastClosing = line.reverse.takeWhile(_ != closing).count(_ == opening)
      val relevant = total - closingBeforeFirstOpening - openingAfterLastClosing

      val hasClosing = lineAfterCaret.contains(closing) && !lineAfterCaret.takeWhile(_ != closing).contains(opening)
      val hasOpening = lineBeforeCaret.contains(opening) && !lineBeforeCaret.reverse.takeWhile(_ != opening).contains(closing)

      def isNested =
        document.textRangeOpt(offset-1, offset+1) exists (Set("{}", "[]", "()", "<>", "\"\"")(_))

      if (hasOpening && hasClosing)
        relevant <= 0
      else
        Character.isWhitespace(lineAfterCaret(0)) || isNested
    }
  }
}
