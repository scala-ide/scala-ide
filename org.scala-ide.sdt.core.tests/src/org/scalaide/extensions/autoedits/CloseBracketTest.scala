package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class CloseBracketTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new CloseBracket {
    override val document = doc
    override val textChange = change
  }

  val bracket = Add("[")

  @Test
  def auto_close_opening() =
    "^" becomes "[[[]]]^" after bracket

  @Test
  def auto_close_nested_opening() =
    "[^]" becomes "[[[[]]]^]" after bracket

  @Test
  def auto_close_after_pending_closing() =
    "], List[^]" becomes "], List[[[[]]]^]" after bracket

  @Test
  def prevent_auto_closing_when_caret_before_non_white_space() =
    "^m" becomes "[^m" after bracket

  @Test
  def auto_closing_when_caret_after_def_declaration() =
    "def m^(i: Int)" becomes "def m[[[]]]^(i: Int)" after bracket

  @Test
  def auto_closing_when_caret_after_class_declaration() =
    "private[pkg] class X^(i: Int)" becomes "private[pkg] class X[[[]]]^(i: Int)" after bracket

  @Test
  def auto_closing_when_caret_before_white_space() =
    "def m^ (i: Int)" becomes "def m[[[]]]^ (i: Int)" after bracket

  @Test
  def auto_closing_before_white_space_at_end_of_line() =
    "^   " becomes "[[[]]]^   " after bracket

  @Test
  def no_auto_closing_on_missing_opening() =
    "[^]]" becomes "[[^]]" after bracket
}
