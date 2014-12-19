package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.junit.Test

class CloseParenthesisTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new CloseParenthesis {
    override val document = doc
    override val textChange = change
  }

  val paren = Add("(")

  @Test
  def auto_close_opening() =
    "^" becomes "([[]])^" after paren

  @Test
  def auto_close_nested_opening() = {
    "{[(<\"" zip "}])>\"" foreach {
      case (o, c) ⇒
        s"$o^$c" becomes s"$o([[]])^$c" after paren
    }
  }

  @Test
  def no_auto_close_in_char_literal() =
    "'^'" becomes "'(^'" after paren

  @Test
  def auto_close_after_pending_closing() =
    ") map (^)" becomes ") map (([[]])^)" after paren

  @Test
  def prevent_auto_closing__when_caret_before_non_white_space() =
    "List(1) map ^(_+1)" becomes "List(1) map (^(_+1)" after paren

  @Test
  def no_auto_close_before_non_white_space_in_parenthesis() =
    "(^string)" becomes "((^string)" after paren

  @Test
  def auto_closing_when_caret_before_white_space() =
    "List(1) map^ (_+1)" becomes "List(1) map([[]])^ (_+1)" after paren

  @Test
  def auto_closing_before_white_space_at_end_of_line() =
    "^   " becomes "([[]])^   " after paren

  @Test
  def no_auto_closing_on_missing_opening() =
    "List(1) map (^))" becomes "List(1) map ((^))" after paren
}
