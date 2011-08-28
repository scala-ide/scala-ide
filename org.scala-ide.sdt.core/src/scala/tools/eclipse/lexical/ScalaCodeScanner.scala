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
import scala.tools.eclipse.properties._
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jface.util.PropertyChangeEvent

class ScalaCodeScanner(val colorManager: IColorManager, val preferenceStore: IPreferenceStore) extends AbstractScalaScanner {

  def nextToken(): IToken = {
    val scalaToken = lexer.nextToken()
    getTokenLength = scalaToken.length
    getTokenOffset = scalaToken.startIndex + offset
    scalaToken.tokenType match {
      case WS => Token.WHITESPACE
      case EOF => Token.EOF
      case _ => getToken(ScalariformToSyntaxClass(scalaToken))
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

}