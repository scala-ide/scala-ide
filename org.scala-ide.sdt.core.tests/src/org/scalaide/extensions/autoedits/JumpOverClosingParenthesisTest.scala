package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class JumpOverClosingParenthesisTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new JumpOverClosingParenthesis {
    override val document = doc
    override val textChange = change
  }

  @Test
  def add_closing() =
    "^" becomes ")^" after Add(")")

  @Test
  def jump_over_closing() =
    ")^)" becomes "))^" after Add(")")

}
