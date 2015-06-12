package org.scalaide.core.ui

import org.junit.Before
import org.junit.Test
import org.scalaide.ui.internal.editor.autoedits.LiteralAutoEditStrategy
import org.scalaide.ui.internal.preferences.EditorPreferencePage._

class LiteralAutoEditStrategyTest extends AutoEditStrategyTests {

  override val strategy = new LiteralAutoEditStrategy(prefStore)

  @Before
  def startUp(): Unit = {
    enable(P_ENABLE_AUTO_ESCAPE_SIGN, true)
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, true)
  }

  @Test
  def remove_string_pair(): Unit = {
    test(input = """ "^" """, expectedOutput = """ ^ """, operation = Remove("\""))
  }

  @Test
  def remove_string_pair_with_auto_remove_escaped_sign_disabled(): Unit = {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    test(input = """ "^" """, expectedOutput = """ ^ """, operation = Remove("\""))
  }

  @Test
  def remove_character_pair(): Unit = {
    test(input = """ '^' """, expectedOutput = """ ^ """, operation = Remove("'"))
  }

  @Test
  def remove_character_pair_with_auto_remove_escaped_sign_disabled(): Unit = {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    test(input = """ '^' """, expectedOutput = """ ^ """, operation = Remove("'"))
  }

  // the following tests belong to existing character literals,
  // i.e. two apostrophes '' are already created

  @Test
  def add_apostrophe_to_character_literal(): Unit = {
    test(input = """ '^' """, expectedOutput = """ ''^' """, operation = Add("'"))
  }

  @Test
  def jump_not_over_closing_character_literal_if_it_is_filled(): Unit = {
    test(input = """ ' ^' """, expectedOutput = """ ' '^' """, operation = Add("'"))
  }

  @Test
  def do_not_auto_escape_character_literal(): Unit = {
    test(input = """ '^' """, expectedOutput = """ ''^' """, operation = Add("'"))
  }

  @Test
  def remove_escaped_character_literal(): Unit = {
    test(input = """ '\'^' """, expectedOutput = """ '^' """, operation = Remove("'"))
  }

  @Test
  def not_remove_escaped_character_literal_if_feature_deactivated(): Unit = {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    test(input = """ '\'^' """, expectedOutput = """ '\^' """, operation = Remove("'"))
  }

  @Test
  def auto_escape_backslash(): Unit = {
    test(input = """ '^' """, expectedOutput = """ '\\^' """, operation = Add("\\"))
  }

  @Test
  def no_auto_escape_backslash_if_feature_deactivated(): Unit = {
    enable(P_ENABLE_AUTO_ESCAPE_SIGN, false)
    test(input = """ '^' """, expectedOutput = """ '\^' """, operation = Add("\\"))
  }

  @Test
  def no_auto_escape_backslash_on_preceding_backslash(): Unit = {
    test(input = """ '\^' """, expectedOutput = """ '\\^' """, operation = Add("\\"))
  }

  @Test
  def remove_escape_backslash(): Unit = {
    test(input = """ '\\^' """, expectedOutput = """ '^' """, operation = Remove("\\"))
  }

  @Test
  def not_remove_escape_backslash_if_feature_deactivated(): Unit = {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    test(input = """ '\\^' """, expectedOutput = """ '\^' """, operation = Remove("\\"))
  }

  @Test
  def remove_whole_character_literal(): Unit = {
    test(input = """ '''^ """, expectedOutput = """ ^ """, operation = Remove("'''"))
  }

  @Test
  def remove_escaped_sign(): Unit = {
    """btnfr"'\""" foreach { c =>
      test(input = """ '\%c^' """ format c, expectedOutput = """ '^' """, operation = Remove(c.toString))
    }
  }

  @Test
  def not_remove_escaped_sign_if_feature_deactivated(): Unit = {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    """btnfr"'\""" foreach { c =>
      test(input = """ '\%c^' """ format c, expectedOutput = """ '\^' """, operation = Remove(c.toString))
    }
  }

  @Test
  def remove_escaped_sign_with_caret_on_backslash(): Unit = {
    """btnfr"'\""" foreach { c =>
      test(input = """ '\^%c' """ format c, expectedOutput = """ '^' """, operation = Remove("\\"))
    }
  }

  @Test
  def not_remove_escaped_sign_on_backslash_if_feature_deactivated(): Unit = {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    """btnfr"'\""" foreach { c =>
      test(input = """ '\^%c' """ format c, expectedOutput = """ '^%c' """ format c, operation = Remove("\\"))
    }
  }

  @Test
  def not_remove_escaped_char_literal_if_it_is_not_terminated(): Unit = {
    test(input = """ '\^'; """, expectedOutput = """ '^'; """, operation = Remove("\\"))
  }

  @Test
  def not_remove_escaped_char_literal_if_it_is_not_terminated_and_last_sign_is_the_literal(): Unit = {
    test(input = """ '\^'""", expectedOutput = """ '^'""", operation = Remove("\\"))
  }

  @Test
  def not_add_escaped_char_literal_if_it_is_not_terminated(): Unit = {
    test(input = """ 'n^ """, expectedOutput = """ 'n'^ """, operation = Add("'"))
  }

  // the following tests belong to multi-line string literals

  @Test
  def auto_close_multi_line_string_literal(): Unit = {
    test(input = " \"\"^ ", expectedOutput = " \"\"\"^\"\"\" ", operation = Add("\""))
  }

  @Test
  def not_add_closing_string_literal_if_it_occurs_before_text(): Unit = {
    test(input = """ ^text """, expectedOutput = """ "^text """, operation = Add("\""))
  }
}
