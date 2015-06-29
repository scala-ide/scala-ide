package org.scalaide.extensions
package autoedits

import org.eclipse.jface.text.IRegion
import org.scalaide.util.eclipse.RegionUtils._

/** Functionality for all auto edits that should handle matching pairs. */
trait CloseMatchingPair extends AutoEdit {

  private val openingMap = Map('{' -> '}', '[' -> ']', '(' -> ')', '<' -> '>', '"' -> '"')
  private val closingMap = openingMap.map(_.swap)

  def opening: Char
  def closing: Char

  def singleLinkedPos(pos: Int): Seq[Seq[(Int, Int)]] =
    Seq(Seq((pos, 0)))

  /**
   * Checks if it is necessary to insert a closing element. Normally this is
   * always the case with exception of these cases:
   *
   * 1. The caret is positioned directly before non white space
   * 2. There are unmatched closing elements after the caret position
   */
  def autoClosingRequired(offset: Int): Boolean = {

    /**
     * Searches for all pairing elements leftwards, starting at `startPosition`
     * (inclusive) and ending at `endPosition` (inclusive). If a matching pair
     * is found, the elements of this pair are not returned. The only elements
     * that are returned are the ones which are unpaired and therefore are
     * important to consider in auto closing.
     */
    def searchPairElemsLeftwards(startPosition: Int, endPosition: Int): List[Char] = {
      @annotation.tailrec
      def loop(offset: Int, st: List[Char]): List[Char] =
        if (offset < endPosition) st
        else {
          val c = document(offset)
          if (openingMap contains c)
            if (st.headOption contains openingMap(c))
              loop(offset-1, st.tail)
            else
              loop(offset-1, c :: st)
          else if (closingMap contains c)
            loop(offset-1, c :: st)
          else
            loop(offset-1, st)
        }
      loop(startPosition, Nil).reverse
    }

    /**
     * In contrast to [[searchPairElemsLeftwards]] this searches for elements
     * rightwards, starting at `startPosition` (inclusive) and ending at
     * `endPosition` (exclusive). If a matching pair is found, the elements of
     * this pair are not returned. The only elements that are returned are the
     * ones which are unpaired and therefore are important to consider in auto
     * closing.
     */
    def searchPairElemsRightwards(startPosition: Int, endPosition: Int): List[Char] = {
      @annotation.tailrec
      def loop(offset: Int, st: List[Char]): List[Char] =
        if (offset >= endPosition) st
        else {
          val c = document(offset)
          if (closingMap contains c)
            if (st.headOption contains closingMap(c))
              loop(offset+1, st.tail)
            else
              loop(offset+1, c :: st)
          else if (openingMap contains c)
            loop(offset+1, c :: st)
          else
            loop(offset+1, st)
        }
      loop(startPosition, Nil).reverse
    }

    val lineInfo = document.lineInformationOfOffset(offset)
    val lineAfterCaret = document.textRange(offset, lineInfo.end)

    if (lineAfterCaret.isEmpty) true
    else {
      val elemsLeft = searchPairElemsLeftwards(offset-1, lineInfo.start)
      val elemsRight = searchPairElemsRightwards(offset, lineInfo.end)

      val closingCount = elemsRight.takeWhile(_ != opening).count(_ == closing)
      val openingCount = elemsLeft.takeWhile(_ != closing).count(_ == opening)
      val isUnbalanced = closingCount-openingCount > 0

      def isNested =
        (closingMap contains document(offset)) &&
        elemsLeft.headOption.exists(c => elemsRight.headOption == openingMap.get(c))

      if (isUnbalanced)
        false
      else
        Character.isWhitespace(lineAfterCaret(0)) || isNested
    }
  }
}
