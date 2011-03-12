package scala.tools.eclipse.lexical

import org.eclipse.jface.text._
import org.eclipse.jface.text.rules._
import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.jdt.ui.text.IJavaColorConstants._
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlightings

import org.eclipse.jdt.internal.ui.text.AbstractJavaScanner

import scalariform.lexer.{ ScalaLexer, UnicodeEscapeReader, ScalaOnlyLexer }
import scalariform.lexer.Tokens._

class ScalaCodeScanner(manager: IColorManager, store: IPreferenceStore) extends ITokenScanner {

  def nextToken(): IToken = {
    val scalaToken = lexer.nextToken()
    getTokenLength = scalaToken.length
    getTokenOffset = scalaToken.startIndex + offset
    import ColourGrabberScanner.getToken
    scalaToken.tokenType match {
      case WS => Token.WHITESPACE
      case PLUS | MINUS | STAR | PIPE | TILDE | EXCLAMATION => getToken(JAVA_OPERATOR)
      case DOT  | COMMA | COLON | USCORE | EQUALS | SEMI | LARROW | ARROW | SUBTYPE | SUPERTYPE | VIEWBOUND => getToken(JAVA_OPERATOR)
      case STRING_LITERAL => getToken(JAVA_STRING)
      case LPAREN | RPAREN | LBRACE | RBRACE | LBRACKET | RBRACKET => getToken(JAVA_BRACKET)
      case RETURN => getToken(JAVA_KEYWORD_RETURN)
      case EOF => Token.EOF
      case TRUE | FALSE | NULL => getToken(JAVA_KEYWORD)
      case VARID if ScalaOnlyLexer.isOperatorPart(scalaToken.getText(0)) => getToken(JAVA_OPERATOR)
      case t if t.isKeyword => getToken(JAVA_KEYWORD)
      case _ => getToken(JAVA_DEFAULT)
    }
  }

  var getTokenOffset: Int = _
  var getTokenLength: Int = _
  private var document: IDocument = _
  private var lexer: ScalaLexer = _
  private var offset: Int = _

  def setRange(document: IDocument, offset: Int, length: Int) {
    this.document = document
    this.offset = offset
    this.lexer = new ScalaLexer(new UnicodeEscapeReader(document.get(offset, length), forgiveLexerErrors = true), forgiveErrors = true)
  }

  /** Just as an easy way to get the Java styles */
  private object ColourGrabberScanner extends AbstractJavaScanner(manager, store) {

    override def getToken(key: String) = super.getToken(key) // increase visibility

    def createRules: java.util.List[IRule] = new java.util.ArrayList[IRule]

    val getTokenProperties = Array(JAVA_KEYWORD, JAVA_STRING, JAVA_DEFAULT, JAVA_KEYWORD_RETURN, JAVA_OPERATOR, JAVA_BRACKET)

    initialize()
  }

}