package scala.tools.eclipse.ui

import org.junit.Test

class LiteralAutoEditStrategyTest extends AutoEditStrategyTests(new LiteralAutoEditStrategy) {

  @Test
  def auto_close_string_literal() {
    test(input = "^", expectedOutput = "\"^\"", operation = Add("\""))
  }

  @Test
  def remove_string_pair() {
    test(input = """ "^" """, expectedOutput = """ ^ """, operation = Remove("\""))
  }

  @Test
  def no_auto_close_on_existing_string_literal_before_caret() {
    test(input = "\"^", expectedOutput = "\"\"^", operation = Add("\""))
  }

  @Test
  def no_auto_close_on_existing_string_literal_after_caret() {
    test(input = "^\"", expectedOutput = "\"^\"", operation = Add("\""))
  }

  @Test
  def auto_close_character_literal() {
    test(input = "^", expectedOutput = "'^'", operation = Add("'"))
  }

  @Test
  def remove_character_pair() {
    test(input = """ '^' """, expectedOutput = """ ^ """, operation = Remove("'"))
  }

  @Test
  def no_auto_close_on_existing_char_literal_before_caret() {
    test(input = "'^", expectedOutput = "''^", operation = Add("'"))
  }

  @Test
  def no_auto_close_on_existing_char_literal_after_caret() {
    test(input = "^'", expectedOutput = "'^'", operation = Add("'"))
  }

  // the following tests belong to existing character literals,
  // i.e. two apostrophes '' are already created

  @Test
  def add_apostrophe_to_character_literal() {
    test(input = """ '^' """, expectedOutput = """ ''^' """, operation = Add("'"))
  }

  @Test
  def jump_not_over_closing_character_literal_if_it_is_filled() {
    test(input = """ ' ^' """, expectedOutput = """ ' '^' """, operation = Add("'"))
  }

  @Test
  def do_not_auto_escape_character_literal() {
    test(input = """ '^' """, expectedOutput = """ ''^' """, operation = Add("'"))
  }

  @Test
  def remove_escaped_character_literal() {
    test(input = """ '\'^' """, expectedOutput = """ '^' """, operation = Remove("'"))
  }

  @Test
  def auto_escape_backslash() {
    test(input = """ '^' """, expectedOutput = """ '\\^' """, operation = Add("\\"))
  }

  @Test
  def no_auto_escape_backslash_on_preceding_backslash() {
    test(input = """ '\^' """, expectedOutput = """ '\\^' """, operation = Add("\\"))
  }

  @Test
  def remove_escape_backslash() {
    test(input = """ '\\^' """, expectedOutput = """ '^' """, operation = Remove("\\"))
  }
}