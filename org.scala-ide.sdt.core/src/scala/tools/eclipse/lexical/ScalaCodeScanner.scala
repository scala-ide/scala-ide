package scala.tools.eclipse.lexical

import org.eclipse.jface.text._
import org.eclipse.jface.text.rules._
import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlightings
import org.eclipse.jdt.internal.ui.text.AbstractJavaScanner
import scalariform.lexer.{ ScalaLexer, UnicodeEscapeReader, ScalaOnlyLexer }
import scalariform.lexer.Tokens._
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jface.util.PropertyChangeEvent
import scala.tools.eclipse.properties.syntaxcolouring.ScalariformToSyntaxClass
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses
import scala.annotation.tailrec

class ScalaCodeScanner(val colorManager: IColorManager, val preferenceStore: IPreferenceStore) extends AbstractScalaScanner {

  def nextToken(): IToken = {
    val current = tokens(pos)

    getTokenLength = current.length
    getTokenOffset = current.offset + offset

    /**
     * Heuristic to distinguish the macro keyword from uses as an identifier. To be 100% accurate requires a full parse, 
     * which would be too slow, but this is hopefully adequate.
     */
    def isMacro =
      current.tokenType.isId && current.text == "macro" &&
        scanForward(pos + 1).exists(token => token.tokenType.isId && Character.isUnicodeIdentifierStart(token.text(0))) &&
        scanBackward(pos - 1).exists(_.tokenType == EQUALS)

    val result =
      current.tokenType match {
        case WS => Token.WHITESPACE
        case EOF => Token.EOF
        case _ =>
          if (isMacro)
            getToken(ScalaSyntaxClasses.KEYWORD)
          else
            getToken(ScalariformToSyntaxClass(current))
      }
    if (pos + 1 < tokens.length) 
      pos += 1
    result
  }

  @tailrec
  private def scanForward(pos: Int): Option[scalariform.lexer.Token] =
    if (pos > tokens.length)
      None
    else {
      val token = tokens(pos)
      token.tokenType match {
        case WS | LINE_COMMENT | MULTILINE_COMMENT =>
          scanForward(pos + 1)
        case _ =>
          Some(token)
      }
    }

  @tailrec
  private def scanBackward(pos: Int): Option[scalariform.lexer.Token] =
    if (pos <= 0)
      None
    else {
      val token = tokens(pos)
      token.tokenType match {
        case WS | LINE_COMMENT | MULTILINE_COMMENT =>
          scanBackward(pos - 1)
        case _ =>
          Some(token)
      }
    }

  var getTokenOffset: Int = _
  var getTokenLength: Int = _

  private var tokens: Array[scalariform.lexer.Token] = Array()
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