package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class JumpOverClosingCurlyBraceTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new JumpOverClosingCurlyBrace {
    override val document = doc
    override val textChange = change
  }

  @Test
  def add_closing_brace() =
    "^" becomes "}^" after Add("}")

  @Test
  def jump_over_closing_brace() =
    "}^}" becomes "}}^" after Add("}")

}
