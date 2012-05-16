package scala.tools.eclipse.lexical

import scala.annotation.tailrec
import scala.tools.eclipse.properties.syntaxcolouring._

import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.rules._

import scalariform._
import scalariform.lexer.ScalaLexer
import scalariform.lexer.{ Token => ScalariformToken }
import scalariform.lexer.Tokens._

class ScalaCodeScanner(val colorManager: IColorManager, val preferenceStore: IPreferenceStore, scalaVersion: ScalaVersion) extends AbstractScalaScanner {

  def nextToken(): IToken = {
    val token = tokens(pos)

    getTokenLength = token.length
    getTokenOffset = token.offset + offset

    val result = token.tokenType match {
      case WS                  => Token.WHITESPACE
      case EOF                 => Token.EOF
      case _ if isMacro(token) => getToken(ScalaSyntaxClasses.KEYWORD)
      case _                   => getToken(ScalariformToSyntaxClass(token))
    }
    if (pos + 1 < tokens.length)
      pos += 1
    result
  }

  /**
   * Heuristic to distinguish the macro keyword from uses as an identifier. To be 100% accurate requires a full parse,
   * which would be too slow, but this is hopefully adequate.
   */
  private def isMacro(token: ScalariformToken) =
    scalaVersion >= ScalaVersions.Scala_2_10 &&
      token.tokenType.isId && token.text == "macro" &&
      findMeaningfulToken(pos + 1, shift = 1).exists(token => token.tokenType.isId) &&
      findMeaningfulToken(pos - 1, shift = -1).exists(_.tokenType == EQUALS)

  /**
   * Scan forwards or backwards for nearest comment that is neither whitespace nor comment
   */
  @tailrec
  private def findMeaningfulToken(pos: Int, shift: Int): Option[ScalariformToken] =
    if (pos <= 0 || pos > tokens.length)
      None
    else {
      val token = tokens(pos)
      token.tokenType match {
        case WS | LINE_COMMENT | MULTILINE_COMMENT =>
          findMeaningfulToken(pos + shift, shift)
        case _ =>
          Some(token)
      }
    }

  var getTokenOffset: Int = _
  var getTokenLength: Int = _

  private var tokens: Array[ScalariformToken] = Array()
  private var pos = 0

  private var document: IDocument = _
  private var offset: Int = _

  def setRange(document: IDocument, offset: Int, length: Int) {
    this.document = document
    this.offset = offset
    val source = document.get(offset, length)
    val lexer = ScalaLexer.createRawLexer(source, forgiveErrors = true)
    this.tokens = lexer.toArray
    this.pos = 0
  }

}