package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class CloseBacktickTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new CloseBacktick {
    override val document = doc
    override val textChange = change
  }

  val backtick = Add("`")

  @Test
  def auto_close_opening() =
    "^" becomes "`[[]]`^" after backtick

  @Test
  def no_auto_close_in_char_literal() =
    "'^'" becomes "'`^'" after backtick

  @Test
  def no_auto_close_on_existing_backtick_before_caret1() =
    "`^" becomes "``^" after backtick

  @Test
  def no_auto_close_on_existing_backtick_before_caret2() =
    "`hello^" becomes "`hello`^" after backtick

  @Test
  def no_auto_close_on_existing_backtick_before_caret3() =
    "`hello ? abc^" becomes "`hello ? abc`^" after backtick

  @Test
  def no_auto_close_on_existing_backtick_before_caret4() =
    "`xx x`; `hello ? abc^" becomes "`xx x`; `hello ? abc`^" after backtick

  @Test
  def no_auto_close_before_text() =
    "^c" becomes "`^c" after backtick
}
