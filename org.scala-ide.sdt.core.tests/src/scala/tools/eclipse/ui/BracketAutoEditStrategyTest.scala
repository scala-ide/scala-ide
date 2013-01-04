package scala.tools.eclipse.ui

import org.junit.Test

class BracketAutoEditStrategyTest extends AutoEditStrategyTests(new BracketAutoEditStrategy) {

  @Test
  def auto_closing_opening_brace() {
    test(input = "^", expectedOutput = "{^}", operation = Add("{"))
  }

  @Test
  def remove_brace_pair() {
    test(input = "{^}", expectedOutput = "^", operation = Remove("{"))
  }

  @Test
  def remove_into_brace_pair() {
    test(input = "{}abc^", expectedOutput = "{^", operation = Remove("}abc"))
  }

  @Test
  def jump_over_closing_brace() {
    test(input = "}^}", expectedOutput = "}}^", operation = Add("}"))
  }

  @Test
  def add_closing_brace() {
    test(input = "^", expectedOutput = "}^", operation = Add("}"))
  }

  @Test
  def remove_opening_brace() {
    test(input = "{^", expectedOutput = "^", operation = Remove("{"))
  }
}