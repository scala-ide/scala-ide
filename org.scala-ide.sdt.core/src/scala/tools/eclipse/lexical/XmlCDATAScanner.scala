package scala.tools.eclipse.lexical
import org.eclipse.jface.text._
import org.eclipse.jface.text.rules._
import org.eclipse.jdt.ui.text.IColorManager
import scala.tools.eclipse.lexical.XmlColours._
import org.eclipse.swt.graphics.RGB
import scala.collection.mutable.ListBuffer

class XmlCDATAScanner(colorManager: IColorManager) extends ITokenScanner {

  import XmlCDATAScanner._

  private def tokenData(colourOpt: Option[RGB]) = colourOpt match {
    case Some(colour) => new TextAttribute(colorManager.getColor(colour))
    case None => null
  }

  abstract trait IRegionToken extends IToken with IRegion
  case class RegionToken(getOffset: Int, getLength: Int, colourOpt: Option[RGB]) extends Token(tokenData(colourOpt)) with IRegion with IRegionToken

  /**
   * Dummy token to sit on top of the stack until the first call to nextToken() removes it
   */
  case object DummyRegionToken extends Token(null) with IRegionToken {
    val getLength = 0
    val getOffset = 0
  }

  private var regionTokens: List[IRegionToken] = Nil

  def setRange(document: IDocument, offset: Int, length: Int) {
    var buffer = new ListBuffer[IRegionToken]
    buffer += DummyRegionToken
    buffer += RegionToken(offset, CDATA_START.length, Some(XML_CDATA_BORDER))
    if (length > CDATA_START.length) {
      if (length < CDATA_START.length + CDATA_END.length - 1)
        buffer += RegionToken(offset + CDATA_START.length, length - CDATA_START.length, None)
      else {
        val contentStart = offset + CDATA_START.length
        val contentEnd = offset + length - CDATA_END.length - 1
        val contentLength = contentEnd - contentStart + 1
        val endText = document.get(contentEnd + 1, CDATA_END.length)
        if (endText == CDATA_END) {
          if (contentLength > 0)
            buffer += RegionToken(contentStart, contentLength, None)
          buffer += RegionToken(contentEnd + 1, CDATA_END.length, Some(XML_CDATA_BORDER))
        } else
          buffer += RegionToken(contentStart, contentLength + CDATA_END.length, None)
      }
    }
    regionTokens = buffer.toList
  }

  def nextToken(): IToken =
    regionTokens match {
      case Nil | (_:: Nil) => Token.EOF
      case (_:: remainderTokens) =>
        regionTokens = remainderTokens
        remainderTokens.head
    }

  def getTokenOffset = regionTokens.head.getOffset

  def getTokenLength = regionTokens.head.getLength

}

object XmlCDATAScanner {

  val CDATA_START = "<![CDATA["
	  
  val CDATA_END = "]]>"

}