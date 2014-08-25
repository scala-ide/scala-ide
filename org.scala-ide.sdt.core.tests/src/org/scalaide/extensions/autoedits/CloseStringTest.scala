package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class CloseStringTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new CloseString {
    override val document = doc
    override val textChange = change
  }

  val stringLit = Add("\"")

  @Test
  def auto_close_opening() =
    "^" becomes "\"[[]]\"^" after stringLit

  @Test
  def auto_close_nested_opening() = {
    "{[(<" zip "}])>" foreach {
      case (o, c) ⇒
        s"$o^$c" becomes s"""$o"[[]]"^$c""" after stringLit
    }
  }

  @Test
  def no_auto_close_in_char_literal() =
    "'^'" becomes "'\"^'" after stringLit

  @Test
  def no_auto_close_on_existing_string_literal_before_caret() =
    "\"^" becomes "\"\"^" after stringLit

  @Test
  def no_auto_close_on_existing_string_literal_after_caret() =
    "^\"" becomes "\"^\"" after stringLit

  @Test
  def no_auto_close_before_text() =
    " ^value" becomes " \"^value" after stringLit
}
