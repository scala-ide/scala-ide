package org.scalaide.core.lexical

import org.scalaide.ui.syntax.ScalaSyntaxClass

/** Support to tokenize Scala code.
 */
trait ScalaCodeTokenizer {
  
  import ScalaCodeTokenizer.Token

  /**
   * Tokenizes a string of Scala code.
   *
   * @param contents the string to tokenize
   * @param offset If `contents` is a snippet within a larger document, use `offset` to indicate it `contents` offset within the larger document so that resultant tokens are properly positioned with respect to the larger document.
   * @return an sequence of the tokens for the given string
   */
  def tokenize(contents: String, offset: Int = 0): IndexedSeq[Token]
  
}

object ScalaCodeTokenizer {
  
  /** A Scala token.
   *  
   *  @param offset the position of the first character of the token
   *  @param length the length of the token
   *  @param syntaxClass the class of the token
   */
  case class Token(offset: Int, length: Int, syntaxClass: ScalaSyntaxClass)
}