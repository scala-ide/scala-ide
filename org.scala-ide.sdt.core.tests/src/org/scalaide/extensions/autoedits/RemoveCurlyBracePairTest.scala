package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class RemoveCurlyBracePairTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new RemoveCurlyBracePair {
    override val document = doc
    override val textChange = change
  }

  @Test
  def remove_brace_pair() =
    "{^}" becomes "^" after Remove("{")

  @Test
  def remove_into_brace_pair() =
    "{}abc^" becomes "{^" after Remove("}abc")

  @Test
  def remove_opening_brace() =
    "{^" becomes "^" after Remove("{")
}
