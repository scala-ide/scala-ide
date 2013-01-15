package scala.tools.eclipse.lexical

import scala.annotation.tailrec
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClass

import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.rules.{ IToken, Token }

/**
 * Scans single line strings and divides them into normal string and escape
 * sequence tokens.
 */
class StringTokenScanner(
  escapeSequenceClass: ScalaSyntaxClass,
  stringClass: ScalaSyntaxClass,
  val colorManager: IColorManager,
  val preferenceStore: IPreferenceStore)
    extends AbstractScalaScanner with StringTokenizer {

  private var offset: Int = _
  private var length: Int = _
  private var ranges: IndexedSeq[StyleRange] = _
  private var index: Int = _

  def setRange(document: IDocument, offset: Int, length: Int) {
    ranges = tokenize(document, offset, length)
    index = 0

    val StyleRange(start, end, _) = ranges(index)
    this.offset = start
    this.length = end - start
  }

  def nextToken(): IToken =
    if (index >= ranges.size)
      Token.EOF
    else {
      val StyleRange(start, end, style) = ranges(index)
      val tok = getToken(style match {
        case EscapeSequence => escapeSequenceClass
        case NormalString   => stringClass
      })
      index += 1
      offset = start
      length = end - start
      tok
    }

  def getTokenOffset(): Int = offset

  def getTokenLength(): Int = length

}

/**
 * Separation of tokenizing logic from the `StringTokenScanner`.
 */
trait StringTokenizer {

  /** Denotes a set of possible styles a string can be separated to. */
  sealed abstract class Style
  case object NormalString extends Style
  case object EscapeSequence extends Style

  /**
   * The start index denotes the position BEFORE the first sign of the range
   * whereas the end index denotes the position AFTER the last sign. This means
   * that for an arbitrary string the following is true:
   *
   * string: Hello World!
   * start : 0
   * end   : 12
   * length: end - start = 12
   *
   * If a range spans the whole content (as in the example above) the start index
   * is always 0 whereas the end index is always equal to the length of the input.
   */
  case class StyleRange(start: Int, end: Int, style: Style)

  /** Tokenizes a string given by its offset and length in a document. */
  def tokenize(document: IDocument, offset: Int, length: Int): IndexedSeq[StyleRange] = {
    val str = document.get(offset, length)

    def isEscapeSequenceAt(i: Int) =
      if (i >= str.length()) false
      else {
        val c = str.charAt(i)
        """btnfr"'\""".contains(c)
      }

    /*
     * Returns the size of an unicode sequence at a given index. The maximum
     * can be 4, the minimum 0. The prefixed 'u' isn't counted.
     */
    def unicodeSequenceLengthAt(i: Int) =
      math.min(4, str.length() - i - 1)

    def isUnicodeSequenceAt(i: Int) =
      if (i >= str.length()) false
      else {
        val len = unicodeSequenceLengthAt(i)
        str.charAt(i) == 'u' && str.substring(i + 1, i + 1 + len).matches("""[\da-fA-F]*""")
      }

    def isOctalSequenceAt(i: Int, len: Int) =
      if (i >= str.length() - len) false
      else {
        str.substring(i, i + len).matches("""[0-7]*""")
      }

    @tailrec
    def findNext(i: Int, xs: IndexedSeq[Int]): IndexedSeq[Int] = {
      def append(len: Int) = xs :+ (i + offset) :+ (i + len + offset)

      if (i >= str.length()) xs
      else str.charAt(i) match {
        case '\\' if isEscapeSequenceAt(i + 1) =>
          findNext(i + 2, append(2))

        case '\\' if isUnicodeSequenceAt(i + 1) =>
          val len = 2 + unicodeSequenceLengthAt(i)
          findNext(i + len, append(len))

        case '\\' if isOctalSequenceAt(i + 1, 3) && str.charAt(i + 1) <= '3' =>
          findNext(i + 4, append(4))

        case '\\' if isOctalSequenceAt(i + 1, 2) =>
          findNext(i + 3, append(3))

        case '\\' if isOctalSequenceAt(i + 1, 1) =>
          findNext(i + 2, append(2))

        case _ =>
          findNext(i + 1, xs)
      }
    }

    /*
     * Optimizes away:
     * - ranges whose length is zero
     * - consecutive ranges of same type
     */
    val optimizedRanges = {
      val indices = offset +: findNext(0, Vector()) :+ (offset + length)
      val ranges = (indices zip indices.tail).zipWithIndex map {
        case ((start, end), i) =>
          StyleRange(start, end, if (i % 2 == 0) NormalString else EscapeSequence)
      }
      (Vector(ranges.head) /: ranges.tail) {
        case (ranges, range @ StyleRange(start, end, style)) =>
          val StyleRange(startBefore, _, styleBefore) = ranges.last
          if (end - start == 0)
            ranges
          else if (styleBefore == style)
            ranges.init :+ StyleRange(startBefore, end, styleBefore)
          else
            ranges :+ range
      }
    }
    optimizedRanges
  }
}