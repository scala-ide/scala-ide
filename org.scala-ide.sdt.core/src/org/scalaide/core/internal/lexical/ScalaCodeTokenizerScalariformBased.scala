package org.scalaide.core.internal.lexical

import org.scalaide.ui.syntax.ScalaSyntaxClass
import scalariform.lexer.ScalaLexer
import org.scalaide.ui.syntax.ScalaSyntaxClasses
import org.scalaide.ui.syntax.ScalariformToSyntaxClass
import scala.annotation.tailrec
import org.eclipse.jface.text.IDocument
import scalariform.ScalaVersion
import scalariform.lexer.{ Token => ScalariformToken }
import scalariform.lexer.Tokens._
import scalariform.lexer.{Token => ScalariformToken}
import org.scalaide.core.lexical.ScalaCodeTokenizer

class ScalaCodeTokenizerScalariformBased(val scalaVersion: ScalaVersion) extends ScalaCodeTokenizer {
  
	import ScalaCodeTokenizer.Token

  def tokenize(contents: String, offset: Int = 0): IndexedSeq[Token] = {
    val token = ScalaLexer.createRawLexer(contents, forgiveErrors = true).toIndexedSeq.init

    /**
     * Heuristic to distinguish the macro keyword from uses as an identifier. To be 100% accurate requires a full parse,
     * which would be too slow, but this is hopefully adequate.
     */
    def isMacro(token: ScalariformToken, pos: Int): Boolean =
      token.tokenType.isId && token.text == "macro" &&
      findMeaningfulToken(pos + 1, shift = 1).exists(token => token.tokenType.isId) &&
      findMeaningfulToken(pos - 1, shift = -1).exists(_.tokenType == EQUALS)

    /**
     * Scan forwards or backwards for nearest token that is neither whitespace nor comment
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
        Token(tok.offset + offset, tok.length, tokenClass(tok, i))
    }
  }

}