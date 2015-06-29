package org.scalaide.util

import scala.tools.nsc.util.Chars.isIdentifierPart
import scala.tools.nsc.util.Chars.isOperatorPart

import org.eclipse.jdt.core.IBuffer
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region

object ScalaWordFinder {

  private def docToSeq(doc: IDocument) = new IndexedSeq[Char] {
    override def apply(i: Int) = doc.getChar(i)
    override def length = doc.getLength
  }

  private def bufferToSeq(buf: IBuffer) = new IndexedSeq[Char] {
    override def apply(i: Int) = buf.getChar(i)
    override def length = buf.getLength
  }

  /** See [[findWord(IndexedSeq[Char],Int):IRegion]]. */
  def findWord(document: IDocument, offset: Int): IRegion =
    findWord(docToSeq(document), offset)

  /** See [[findWord(IndexedSeq[Char],Int):IRegion]]. */
  def findWord(buffer: IBuffer, offset: Int): IRegion =
    findWord(bufferToSeq(buffer), offset)

  /** Find the word enclosing the given `offset`. `$` is not considered part of
   *  an identifier, even though the Scala Specification allows it. We choose this
   *  tradeoff so the word finder does the right thing in interpolated strings, where
   *  `$` is used as a delimiter:
   *
   *  {{{ s"Hello, $name" }}}
   *
   *  Here, the identifier is only `name`.
   */
  def findWord(document: IndexedSeq[Char], offset: Int): IRegion = {
    if (offset < 0 || offset > document.length) throw new IndexOutOfBoundsException("Received an invalid offset for word finding.")

    def find(p: Char => Boolean): IRegion = {
      var start = -2
      var end = -1

      var pos = Math.min(offset - 1, document.size - 1)

      while (pos >= 0 && p(document(pos)))
        pos -= 1

      start = pos

      pos = offset
      val len = document.length
      while (pos < len && p(document(pos)))
        pos += 1

      end = pos

      new Region(start + 1, end - start - 1)
    }

    val idRegion = find(ch => isIdentifierPart(ch) && ch != '$')
    if (idRegion.getLength == 0)
      find(isOperatorPart)
    else
      idRegion
  }

  /** See [[findCompletionPoint(IndexedSeq[Char],Int):IRegion]]. */
  def findCompletionPoint(document: IDocument, offset: Int): IRegion =
    findCompletionPoint(docToSeq(document), offset)

  /** See [[findCompletionPoint(IndexedSeq[Char],Int):IRegion]]. */
  def findCompletionPoint(buffer: IBuffer, offset: Int): IRegion =
    findCompletionPoint(bufferToSeq(buffer), offset)

  /**
   * Find the point after which a completion should be inserted in the document.
   */
  def findCompletionPoint(document: IndexedSeq[Char], offset0: Int): IRegion = {
    def isWordPart(ch: Char) = isIdentifierPart(ch) || isOperatorPart(ch)

    val offset = if (offset0 >= document.length) (document.length - 1) else offset0
    val ch = document(offset)
    if (isWordPart(ch))
      findWord(document, offset)
    else if (offset > 0 && isWordPart(document(offset - 1)))
      findWord(document, offset - 1)
    else
      new Region(offset, 0)
  }

  /** Returns the length of the identifier which is located at the offset position. */
  def identLenAtOffset(doc: IDocument, offset: Int): Int =
    ScalaWordFinder.findWord(doc, offset).getLength()
}
