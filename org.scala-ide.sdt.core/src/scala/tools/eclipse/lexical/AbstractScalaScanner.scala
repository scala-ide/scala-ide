package scala.tools.eclipse.lexical

import org.eclipse.jface.text.rules._
import scala.tools.eclipse.properties.syntaxcoloring.ScalaSyntaxClass
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.preference.IPreferenceStore

trait AbstractScalaScanner extends ITokenScanner {

  protected def preferenceStore: IPreferenceStore

  private var tokens: Map[ScalaSyntaxClass, Token] = Map()

  protected def getToken(syntaxClass: ScalaSyntaxClass): Token =
    tokens.getOrElse(syntaxClass, createToken(syntaxClass))

  private def createToken(syntaxClass: ScalaSyntaxClass) = {
    val token = new Token(getTextAttribute(syntaxClass))
    tokens = tokens + (syntaxClass -> token)
    token
  }

  def adaptToPreferenceChange(event: PropertyChangeEvent) =
    for ((syntaxClass, token) <- tokens)
      token.setData(getTextAttribute(syntaxClass))

  private def getTextAttribute(syntaxClass: ScalaSyntaxClass) = syntaxClass.getTextAttribute(preferenceStore)

}