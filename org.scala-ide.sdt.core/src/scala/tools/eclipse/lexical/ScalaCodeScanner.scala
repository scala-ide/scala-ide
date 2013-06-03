package scala.tools.eclipse.lexical

import scala.annotation.tailrec
import scala.tools.eclipse.properties.syntaxcolouring._

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.rules.{ IToken, Token }

import scalariform.{ ScalaVersion, ScalaVersions }
import scalariform.lexer.{ ScalaLexer, Token => ScalariformToken, Tokens }
import scalariform.lexer.Tokens._

/**
 * Scans Scala source code snippets and divides them into their corresponding
 * token.
 */
class ScalaCodeScanner(
  val preferenceStore: IPreferenceStore,
  val scalaVersion: ScalaVersion)
    extends AbstractScalaScanner with ScalaCodeTokenizer {

  private var ranges: IndexedSeq[Range] = _
  private var index: Int = _
  private var length: Int = _
  private var offset: Int = _

  def setRange(document: IDocument, offset: Int, length: Int) {
    ranges = tokenize(document, offset, length)
    index = 0

    if (!ranges.isEmpty) {
      val Range(start, len, _) = ranges(index)
      this.offset = start
      this.length = len
    }
  }

  def nextToken(): IToken =
    if (index >= ranges.size)
      Token.EOF
    else {
      val Range(start, len, syntaxClass) = ranges(index)
      val tok = getToken(syntaxClass)
      index += 1
      offset = start
      length = len
      tok
    }

  def getTokenOffset(): Int = offset

  def getTokenLength(): Int = length

}

/**
 * Separation of tokenizing logic from the `ScalaCodeScanner`.
 */
trait ScalaCodeTokenizer {

  def scalaVersion: ScalaVersion

  /**
   * The start index denotes the position BEFORE the first sign of the range and
   * the length denotes the number of characters the range spans.
   *
   * `syntaxClass` denotes the token type represented by the range.
   */
  case class Range(start: Int, length: Int, syntaxClass: ScalaSyntaxClass)

  /** Tokenizes a string given by its offset and length in a document. */
  def tokenize(document: IDocument, offset: Int, length: Int): IndexedSeq[Range] = {
    val source = document.get(offset, length)
    val token = ScalaLexer.createRawLexer(source, forgiveErrors = true).toIndexedSeq.init

    /**
     * Heuristic to distinguish the macro keyword from uses as an identifier. To be 100% accurate requires a full parse,
     * which would be too slow, but this is hopefully adequate.
     */
    def isMacro(token: ScalariformToken, pos: Int) =
      // TODO: remove check when not supporting < 2.10 anymore
      scalaVersion >= ScalaVersions.Scala_2_10 &&
        token.tokenType.isId && token.text == "macro" &&
        findMeaningfulToken(pos + 1, shift = 1).exists(token => token.tokenType.isId) &&
        findMeaningfulToken(pos - 1, shift = -1).exists(_.tokenType == EQUALS)

    /**
     * Scan forwards or backwards for nearest comment that is neither whitespace nor comment
     */
    @tailrec
    def findMeaningfulToken(pos: Int, shift: Int): Option[ScalariformToken] =
      if (pos <= 0 || pos >= token.length)
        None
      else {
        val tok = token(pos)
        tok.tokenType match {
          case WS | LINE_COMMENT | MULTILINE_COMMENT =>
            findMeaningfulToken(pos + shift, shift)
          case _ =>
            Some(tok)
        }
      }

    /* Denotes the class of a token. */
    def tokenClass(token: ScalariformToken, pos: Int) =
      if (isMacro(token, pos)) ScalaSyntaxClasses.KEYWORD
      else ScalariformToSyntaxClass(token)

    token.zipWithIndex map {
      case (tok, i) =>
        Range(tok.offset + offset, tok.length, tokenClass(tok, i))
    }
  }

}