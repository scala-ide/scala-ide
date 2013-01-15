package scala.tools.eclipse.lexical

import scala.annotation.tailrec
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClass

import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.rules.{ IToken, Token }

/**
 * Scans Scaladoc contents and tokenize them into different style ranges.
 *
 * This Scanner assumes that anything passed to it is already Scaladoc - it does
 * not search for Scaladoc content inside of arbitrary passed input.
 */
class ScaladocTokenScanner(
  scaladocClass: ScalaSyntaxClass,
  annotationClass: ScalaSyntaxClass,
  macroClass: ScalaSyntaxClass,
  val colorManager: IColorManager,
  val preferenceStore: IPreferenceStore)
    extends AbstractScalaScanner with ScaladocTokenizer {

  private val styles = Map[Style, ScalaSyntaxClass](
    Scaladoc -> scaladocClass,
    Annotation -> annotationClass,
    Macro -> macroClass)

  private var offset: Int = _
  private var length: Int = _
  private var ranges: IndexedSeq[StyleRange] = _
  private var index: Int = _

  def setRange(document: IDocument, offset: Int, length: Int) {
    this.index = 0
    this.ranges = tokenize(document, offset, length)

    val sr @ StyleRange(start, end, _) = ranges(index)
    this.offset = start
    this.length = sr.length
  }

  def nextToken(): IToken =
    if (index >= ranges.size)
      Token.EOF
    else {
      val sr @ StyleRange(start, end, style) = ranges(index)
      val tok = getToken(styles(style))
      index += 1
      offset = start
      length = sr.length
      tok
    }

  def getTokenOffset(): Int = offset

  def getTokenLength(): Int = length

}

/**
 * Separation of tokenizing logic from the `ScaladocTokenScanner`.
 */
trait ScaladocTokenizer {

  /** Denotes a set of possible styles for Scaladoc content. */
  sealed abstract class Style
  case object Annotation extends Style
  case object Scaladoc extends Style
  case object Macro extends Style

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
  case class StyleRange(start: Int, end: Int, style: Style = Scaladoc) {
    def length: Int = end - start
  }

  /** Tokenizes a string given by its offset and length in a document. */
  def tokenize(document: IDocument, offset: Int, length: Int): IndexedSeq[StyleRange] = {
    val str = document.get(offset, length).toCharArray()

    def isNumber(c: Char) = c >= '0' && c <= '9'

    def isAlpha(c: Char) = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'

    def isIdent(c: Char) = isAlpha(c) || c == '_' || isNumber(c)

    def styleOf(c: Char) = c match {
      case '@' => Annotation
      case '$' => Macro
      case _   => Scaladoc
    }

    /* Checks the characters before and after the given index if they are identifiers. */
    def isScaladocStyleAt(i: Int) =
      if (i > 0)
        if (isIdent(str(i - 1)) || i + 1 >= str.length) true
        else !isIdent(str(i + 1))
      else i < str.length - 1 && !isIdent(str(i + 1))

    @tailrec
    def findRanges(i: Int, start: Int, style: Style, xs: IndexedSeq[StyleRange]): IndexedSeq[StyleRange] = {
      def append = xs :+ StyleRange(start + offset, i + offset, style)

      if (i >= str.length) append
      else str(i) match {
        case '@' if !isScaladocStyleAt(i) =>
          findRanges(i + 1, i, Annotation, append)

        case '$' if !isScaladocStyleAt(i) =>
          findRanges(i + 1, i, Macro, append)

        case c if !isIdent(c) && style != Scaladoc =>
          val isNextIndexIdent = i + 1 < str.length && isIdent(str(i + 1))
          val nextStyle = if (isNextIndexIdent) styleOf(c) else Scaladoc
          findRanges(i + 1, i, nextStyle, append)

        case _ =>
          findRanges(i + 1, start, style, xs)
      }
    }

    /*
     * Optimizes away:
     * - ranges whose length is zero
     * - consecutive ranges of same type
     */
    val optimizedRanges = {
      val ranges = findRanges(0, 0, Scaladoc, Vector())

      (Vector(ranges.head) /: ranges.tail) {
        case (ranges, range @ StyleRange(_, nextEnd, nextStyle)) =>
          val StyleRange(start, end, styleBefore) = ranges.last
          if (end - start == 0)
            ranges.init :+ range
          else if (styleBefore == nextStyle)
            ranges.init :+ StyleRange(start, nextEnd, styleBefore)
          else
            ranges :+ range
      }
    }

    optimizedRanges
  }

}