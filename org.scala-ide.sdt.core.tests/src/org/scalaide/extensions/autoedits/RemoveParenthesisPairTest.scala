package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class RemoveParenthesisPairTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new RemoveParenthesisPair {
    override val document = doc
    override val textChange = change
  }

  @Test
  def remove_pair() =
    "(^)" becomes "^" after Remove("(")

  @Test
  def remove_into_pair() =
    "()abc^" becomes "(^" after Remove(")abc")

  @Test
  def remove_opening_only() =
    "(^" becomes "^" after Remove("(")

}
