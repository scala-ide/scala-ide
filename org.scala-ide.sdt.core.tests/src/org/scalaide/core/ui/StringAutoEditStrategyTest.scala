package org.scalaide.core.ui

import org.eclipse.jface.text.IDocumentExtension3
import org.junit.Before
import org.junit.Test
import org.scalaide.ui.internal.editor.autoedits.StringAutoEditStrategy
import org.scalaide.ui.internal.preferences.EditorPreferencePage._

class StringAutoEditStrategyTest extends AutoEditStrategyTests {

  val strategy = new StringAutoEditStrategy(IDocumentExtension3.DEFAULT_PARTITIONING, prefStore)

  @Before
  def startUp(): Unit = {
    enable(P_ENABLE_AUTO_ESCAPE_LITERALS, true)
    enable(P_ENABLE_AUTO_ESCAPE_SIGN, true)
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, true)
  }

  @Test
  def jump_over_closing_string(): Unit = {
    test(input = """ "^" """, expectedOutput = """ ""^ """, operation = Add("\""))
  }

  @Test
  def not_jump_over_closing_string_if_it_is_not_empty(): Unit = {
    test(input = """ "abc^" """, expectedOutput = """ "abc\"^" """, operation = Add("\""))
  }

  @Test
  def not_jump_over_closing_string_if_last_sign_is_escaped(): Unit = {
    test(input = """ "abc\"^" """, expectedOutput = """ "abc\"\"^" """, operation = Add("\""))
  }

  @Test
  def auto_escape_string_literal(): Unit = {
    test(input = """ "^ " """, expectedOutput = """ "\"^ " """, operation = Add("\""))
  }

  @Test
  def no_auto_escape_string_literal_if_feature_deactivated(): Unit = {
    enable(P_ENABLE_AUTO_ESCAPE_LITERALS, false)
    test(input = """ "^ " """, expectedOutput = """ ""^ " """, operation = Add("\""))
  }

  @Test
  def no_auto_escape_string_literal_on_preceding_backslash(): Unit = {
    test(input = """ "\^ " """, expectedOutput = """ "\"^ " """, operation = Add("\""))
  }

  @Test
  def auto_escape_string_on_double_preceding_backslash(): Unit = {
    test(input = """ "\\^ " """, expectedOutput = """ "\\\"^ " """, operation = Add("\""))
  }

  @Test
  def no_auto_escape_string_on_double_preceding_backslash_if_feature_deactivated(): Unit = {
    enable(P_ENABLE_AUTO_ESCAPE_LITERALS, false)
    test(input = """ "\\^ " """, expectedOutput = """ "\\"^ " """, operation = Add("\""))
  }

  @Test
  def auto_escape_backslash(): Unit = {
    test(input = """ "^" """, expectedOutput = """ "\\^" """, operation = Add("\\"))
  }

  @Test
  def no_auto_escape_backslash_if_feature_deactivated(): Unit = {
    enable(P_ENABLE_AUTO_ESCAPE_SIGN, false)
    test(input = """ "^" """, expectedOutput = """ "\^" """, operation = Add("\\"))
  }

  @Test
  def no_auto_escape_backslash_on_preceding_backslash(): Unit = {
    test(input = """ "\^" """, expectedOutput = """ "\\^" """, operation = Add("\\"))
  }

  @Test
  def auto_escape_backslash_on_double_preceding_backslash(): Unit = {
    test(input = """ "\\^ " """, expectedOutput = """ "\\\\^ " """, operation = Add("\\"))
  }

  @Test
  def no_auto_escape_backslash_on_double_preceding_backslash_if_feature_deactivated(): Unit = {
    enable(P_ENABLE_AUTO_ESCAPE_SIGN, false)
    test(input = """ "\\^ " """, expectedOutput = """ "\\\^ " """, operation = Add("\\"))
  }

  @Test
  def remove_escaped_sign(): Unit = {
    """btnfr"'\""" foreach { c =>
      test(input = """ "\%c^" """ format c, expectedOutput = """ "^" """, operation = Remove(c.toString))
    }
  }

  @Test
  def not_remove_escaped_sign_if_feature_deactivated(): Unit = {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    """btnfr"'\""" foreach { c =>
      test(input = """ "\%c^" """ format c, expectedOutput = """ "\^" """, operation = Remove(c.toString))
    }
  }

  @Test
  def remove_escaped_sign_with_caret_on_backslash(): Unit = {
    """btnfr"'\""" foreach { c =>
      test(input = """ "\^%c" """ format c, expectedOutput = """ "^" """, operation = Remove("\\"))
    }
  }

  @Test
  def not_remove_escaped_sign_on_backslash_if_feature_deactivated(): Unit = {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    """btnfr"'\""" foreach { c =>
      test(input = """ "\^%c" """ format c, expectedOutput = """ "^%c" """ format c, operation = Remove("\\"))
    }
  }

  @Test
  def not_remove_escaped_sign_on_invalid_escape_sign(): Unit = {
    test(input = """ "\ ^" """, expectedOutput = """ "\^" """, operation = Remove(" "))
  }

  @Test
  def not_remove_escaped_sign_on_invalid_escape_sign_with_caret_on_backslash(): Unit = {
    test(input = """ "\^ " """, expectedOutput = """ "^ " """, operation = Remove("\\"))
  }

  @Test
  def not_remove_escaped_string_literal_if_string_is_not_terminated(): Unit = {
    test(input = """ "\^"; """, expectedOutput = """ "^"; """, operation = Remove("\\"))
  }

  @Test
  def not_remove_escaped_string_literal_if_string_is_not_terminated_and_last_sign_is_the_literal(): Unit = {
    test(input = """ "\^"""", expectedOutput = """ "^"""", operation = Remove("\\"))
  }

  @Test
  def not_add_escaped_string_literal_if_string_is_not_terminated(): Unit = {
    test(input = """ "^ """, expectedOutput = """ ""^ """, operation = Add("\""))
  }
}