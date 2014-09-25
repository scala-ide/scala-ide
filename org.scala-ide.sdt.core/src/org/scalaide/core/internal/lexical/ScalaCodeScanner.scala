package org.scalaide.core.internal.lexical

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.rules.IToken
import org.eclipse.jface.text.rules.Token
import scalariform.ScalaVersion
import org.scalaide.core.lexical.AbstractScalaScanner

/**
 * Scans Scala source code snippets and divides them into their corresponding
 * token.
 */
class ScalaCodeScanner(
  val preferenceStore: IPreferenceStore,
  val scalaVersion: ScalaVersion)
    extends AbstractScalaScanner {

	import org.scalaide.core.lexical.ScalaCodeTokenizer.{Token => SToken}
  
  private val tokenizer = new ScalaCodeTokenizerScalariformBased(scalaVersion)

  private var ranges: IndexedSeq[SToken] = _
  private var index: Int = _
  private var length: Int = _
  private var offset: Int = _

  def setRange(document: IDocument, offset: Int, length: Int) {
    ranges = tokenizer.tokenize(document.get(offset, length), offset)
    index = 0

    if (!ranges.isEmpty) {
      val SToken(start, len, _) = ranges(index)
      this.offset = start
      this.length = len
    }
  }

  def nextToken(): IToken =
    if (index >= ranges.size)
      Token.EOF
    else {
      val SToken(start, len, syntaxClass) = ranges(index)
      val tok = getToken(syntaxClass)
      index += 1
      offset = start
      length = len
      tok
    }

  def getTokenOffset(): Int = offset

  def getTokenLength(): Int = length

}