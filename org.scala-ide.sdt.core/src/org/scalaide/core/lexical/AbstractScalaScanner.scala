package org.scalaide.core.lexical

import org.eclipse.jface.text.rules._
import org.scalaide.ui.syntax.ScalaSyntaxClass
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.preference.IPreferenceStore

/** Base class for Scala specific token scanners.
 */
trait AbstractScalaScanner extends ITokenScanner {

  /** Updates the UI configuration for the tokens managed by this scanner,
   *  according to the new preferences.
   */
  def adaptToPreferenceChange(event: PropertyChangeEvent) =
    for ((syntaxClass, token) <- tokens)
      token.setData(getTextAttribute(syntaxClass))

  /** Returns the preference store used to configure the tokens managed by
   *  this scanner.
   */
  protected def preferenceStore: IPreferenceStore

  /** Returns the token corresponding to the given [[ScalaSyntaxClass]].
   */
  protected def getToken(syntaxClass: ScalaSyntaxClass): Token =
    tokens.getOrElse(syntaxClass, createToken(syntaxClass))

  private var tokens: Map[ScalaSyntaxClass, Token] = Map()

  private def createToken(syntaxClass: ScalaSyntaxClass) = {
    val token = new Token(getTextAttribute(syntaxClass))
    tokens = tokens + (syntaxClass -> token)
    token
  }

  private def getTextAttribute(syntaxClass: ScalaSyntaxClass) = syntaxClass.getTextAttribute(preferenceStore)

}
