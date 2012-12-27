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
}