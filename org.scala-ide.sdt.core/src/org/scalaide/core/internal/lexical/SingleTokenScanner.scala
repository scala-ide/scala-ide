package org.scalaide.core.internal.lexical

import org.eclipse.jface.text._
import org.eclipse.jface.text.rules._
import org.scalaide.ui.syntax.ScalaSyntaxClass
import org.eclipse.jface.preference.IPreferenceStore
import org.scalaide.core.lexical.AbstractScalaScanner

class SingleTokenScanner(
  val preferenceStore: IPreferenceStore, syntaxClass: ScalaSyntaxClass)
    extends AbstractScalaScanner {

  private var offset: Int = _
  private var length: Int = _
  private var consumed = false

  def setRange(document: IDocument, offset: Int, length: Int): Unit = {
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
