package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class CloseCharTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new CloseChar {
    override val document = doc
    override val textChange = change
  }

  val charLit = Add("'")

  @Test
  def auto_close_opening() =
    "^" becomes "'[[]]'^" after charLit

  @Test
  def auto_close_nested_opening() = {
    "{[(<\"" zip "}])>\"" foreach {
      case (o, c) ⇒
        s"$o^$c" becomes s"$o'[[]]'^$c" after charLit
    }
  }

  @Test
  def no_auto_close_in_char_literal() =
    "'^'" becomes "''^'" after charLit

  @Test
  def no_auto_close_on_existing_char_literal_before_caret() =
    "'^" becomes "''^" after charLit

  @Test
  def no_auto_close_on_existing_char_literal_after_caret() =
    "^'" becomes "'^'" after charLit

  @Test
  def no_auto_close_before_text() =
    "^c" becomes "'^c" after charLit
}
