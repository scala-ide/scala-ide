package scala.tools.eclipse.lexical

import org.eclipse.jface.text._
import org.eclipse.jface.text.rules._
import org.eclipse.jface.util.PropertyChangeEvent
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses._
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClass
import org.eclipse.jface.preference.IPreferenceStore

class SingleTokenScanner(
  val preferenceStore: IPreferenceStore, syntaxClass: ScalaSyntaxClass)
    extends AbstractScalaScanner {

  @deprecated("use primary constructor instead", "4.0")
  def this(syntaxClass: ScalaSyntaxClass, preferenceStore: IPreferenceStore) =
    this(preferenceStore, syntaxClass)

  private var offset: Int = _
  private var length: Int = _
  private var consumed = false

  def setRange(document: IDocument, offset: Int, length: Int) {
    this.offset = offset
    this.length = length
    this.consumed = false
  }

  def nextToken(): IToken =
    if (consumed)
      Token.EOF
    else {
      consumed = true
      getToken(syntaxClass)
    }

  def getTokenOffset = offset

  def getTokenLength = length

}
