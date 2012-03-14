package scala.tools.eclipse.lexical
import org.eclipse.jface.text._
import org.eclipse.jface.text.rules._
import org.eclipse.jdt.ui.text.IColorManager
import scala.collection.mutable.ListBuffer
import org.eclipse.jface.util.PropertyChangeEvent
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses._
import org.eclipse.jface.preference.IPreferenceStore

class XmlCDATAScanner(val colorManager: IColorManager, val preferenceStore: IPreferenceStore) extends AbstractScalaScanner {

  import XmlCDATAScanner._

  case class RegionToken(getOffset: Int, getLength: Int, token: Token)

  private var regionTokens: List[RegionToken] = Nil

  def setRange(document: IDocument, offset: Int, length: Int) {
    var buffer = new ListBuffer[RegionToken]
    /**
     * Dummy token to sit on top of the stack until the first call to nextToken() removes it
     */
    buffer += RegionToken(0, 0, getToken(DEFAULT))
    buffer += RegionToken(offset, CDATA_START.length, getToken(XML_CDATA_BORDER))
    if (length > CDATA_START.length) {
      if (length < CDATA_START.length + CDATA_END.length - 1)
        buffer += RegionToken(offset + CDATA_START.length, length - CDATA_START.length, getToken(DEFAULT))
      else {
        val contentStart = offset + CDATA_START.length
        val contentEnd = offset + length - CDATA_END.length - 1
        val contentLength = contentEnd - contentStart + 1
        val endText = document.get(contentEnd + 1, CDATA_END.length)
        if (endText == CDATA_END) {
          if (contentLength > 0)
            buffer += RegionToken(contentStart, contentLength, getToken(DEFAULT))
          buffer += RegionToken(contentEnd + 1, CDATA_END.length, getToken(XML_CDATA_BORDER))
        } else
          buffer += RegionToken(contentStart, contentLength + CDATA_END.length, getToken(DEFAULT))
      }
    }
    regionTokens = buffer.toList
  }

  def nextToken(): IToken =
    regionTokens match {
      case Nil | (_ :: Nil) => Token.EOF
      case _ :: remainderTokens =>
        regionTokens = remainderTokens
        remainderTokens.head.token
    }

  def getTokenOffset = regionTokens.head.getOffset

  def getTokenLength = regionTokens.head.getLength

}

object XmlCDATAScanner {

  val CDATA_START = "<![CDATA["

  val CDATA_END = "]]>"

}