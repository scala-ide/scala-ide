package scala.tools.eclipse.ui

import org.junit.Test

class StringAutoEditStrategyTest extends AutoEditStrategyTests(new StringAutoEditStrategy) {

  @Test
  def jump_over_closing_string() {
    test(input = """ "^" """, expectedOutput = """ ""^ """, operation = Add("\""))
  }

  @Test
  def not_jump_over_closing_string_if_it_is_not_empty() {
    test(input = """ "abc^" """, expectedOutput = """ "abc\"^" """, operation = Add("\""))
  }

  @Test
  def not_jump_over_closing_string_if_last_sign_is_escaped() {
    test(input = """ "abc\"^" """, expectedOutput = """ "abc\"\"^" """, operation = Add("\""))
  }

  @Test
  def auto_escape_string_literal() {
    test(input = """ "^ " """, expectedOutput = """ "\"^ " """, operation = Add("\""))
  }

  @Test
  def no_auto_escape_string_literal_on_preceding_backslash() {
    test(input = """ "\^ " """, expectedOutput = """ "\"^ " """, operation = Add("\""))
  }

  @Test
  def auto_escape_string_on_double_preceding_backslash() {
    test(input = """ "\\^ " """, expectedOutput = """ "\\\"^ " """, operation = Add("\""))
  }

  @Test
  def remove_escaped_string_literal() {
    test(input = """ "\"^" """, expectedOutput = """ "^" """, operation = Remove("\""))
  }

  @Test
  def auto_escape_backslash() {
    test(input = """ "^" """, expectedOutput = """ "\\^" """, operation = Add("\\"))
  }

  @Test
  def no_auto_escape_backslash_on_preceding_backslash() {
    test(input = """ "\^" """, expectedOutput = """ "\\^" """, operation = Add("\\"))
  }

  @Test
  def auto_escape_backslash_on_double_preceding_backslash() {
    test(input = """ "\\^ " """, expectedOutput = """ "\\\\^ " """, operation = Add("\\"))
  }

  @Test
  def remove_escape_backslash() {
    test(input = """ "\\^" """, expectedOutput = """ "^" """, operation = Remove("\\"))
  }

}