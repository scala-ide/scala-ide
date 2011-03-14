package scala.tools.eclipse.lexical

import org.eclipse.jface.text._
import org.eclipse.jface.text.rules._
import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.jface.util.PropertyChangeEvent
import scala.tools.eclipse.properties.ScalaSyntaxClasses._
import scala.tools.eclipse.properties.ScalaSyntaxClass
import org.eclipse.jface.preference.IPreferenceStore

class SingleTokenScanner(
  syntaxClass: ScalaSyntaxClass, val colorManager: IColorManager, val preferenceStore: IPreferenceStore)
  extends AbstractScalaScanner {

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
