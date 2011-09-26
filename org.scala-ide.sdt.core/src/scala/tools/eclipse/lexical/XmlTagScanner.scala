package scala.tools.eclipse.lexical
import org.eclipse.jface.text._
import org.eclipse.jface.text.rules._
import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.jdt.internal.ui.text.CombinedWordRule
import scala.annotation.{ switch, tailrec }
import org.eclipse.swt.SWT
import scala.tools.eclipse.properties.ScalaSyntaxClass
import scala.tools.eclipse.properties.ScalaSyntaxClasses._
import scala.tools.eclipse.properties.ScalaSyntaxClasses
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.preference.IPreferenceStore

class XmlTagScanner(val colorManager: IColorManager, val preferenceStore: IPreferenceStore) extends AbstractScalaScanner {
  import XmlTagScanner._

  var pos: Int = -1

  var end: Int = -1

  var document: IDocument = _

  private var afterTagStart = false

  var tokenOffset: Int = -1

  var tokenLength: Int = -1

  def getTokenOffset = tokenOffset

  def getTokenLength = tokenLength

  def setRange(document: IDocument, offset: Int, length: Int) {
    this.document = document
    this.pos = offset
    this.end = offset + length - 1
  }

  private def ch = if (pos > end) EOF else document.getChar(pos)

  private def ch(lookahead: Int) = {
    val offset = pos + lookahead
    if (offset > end || offset < 0)
      EOF
    else
      document.getChar(offset)
  }

  private def accept() { pos += 1 }

  private def accept(n: Int) { pos += n }

  def nextToken(): IToken = {
    val start = pos
    val wasAfterTagStart = afterTagStart
    afterTagStart = false
    val token: IToken = ch match {
      case '<' =>
        accept()
        afterTagStart = true
        getToken(XML_TAG_DELIMITER)
      case EOF => Token.EOF
      case '\'' =>
        accept()
        getXmlAttributeValue('\'')
        getToken(XML_ATTRIBUTE_VALUE)
      case '"' =>
        accept()
        getXmlAttributeValue('"')
        getToken(XML_ATTRIBUTE_VALUE)
      case '/' if (ch(1) == '>') =>
        accept(2)
        getToken(XML_TAG_DELIMITER)
      case '>' =>
        accept()
        getToken(XML_TAG_DELIMITER)
      case '=' =>
        accept()
        getToken(XML_ATTRIBUTE_EQUALS)
      case ' ' | '\r' | '\n' | '\t' =>
        accept()
        getWhitespace
        Token.WHITESPACE
      case _ if wasAfterTagStart =>
        accept()
        getXmlName
        getToken(XML_TAG_NAME)
      case _ =>
        accept()
        getXmlName
        getToken(XML_ATTRIBUTE_NAME)
    }
    tokenOffset = start
    tokenLength = pos - start
    token
  }

  @tailrec
  private def getXmlName: Unit =
    (ch: @switch) match {
      case ' ' | '\r' | '\n' | '\t' | EOF | '\'' | '\"' | '>' | '/' | '<' | '=' =>
      case _ =>
        accept()
        getXmlName
    }

  @tailrec
  private def getWhitespace: Unit =
    (ch: @switch) match {
      case ' ' | '\r' | '\n' | '\t' =>
        accept()
        getWhitespace
      case _ =>
    }

  @tailrec
  private def getXmlAttributeValue(quote: Char): Unit =
    ch match {
      case EOF =>
      case `quote` =>
        accept()
      case _ =>
        accept()
        getXmlAttributeValue(quote)
    }


}

object XmlTagScanner {

  final val EOF = '\u001A'

}