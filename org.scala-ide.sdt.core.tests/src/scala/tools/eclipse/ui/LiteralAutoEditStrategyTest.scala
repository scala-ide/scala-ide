package scala.tools.eclipse.ui

import scala.tools.eclipse.properties.EditorPreferencePage

import org.eclipse.jface.preference.IPreferenceStore
import org.junit.{ Before, Test }
import org.mockito.Mockito._

object LiteralAutoEditStrategyTest {

  val prefStore = mock(classOf[IPreferenceStore])

  def enableAutoEscape(enable: Boolean) {
    when(prefStore.getBoolean(EditorPreferencePage.P_ENABLE_AUTO_ESCAPE_LITERALS)).thenReturn(enable)
  }
  def enable(property: String, enable: Boolean) {
    when(prefStore.getBoolean(property)).thenReturn(enable)
  }
}

class LiteralAutoEditStrategyTest extends AutoEditStrategyTests(
    new LiteralAutoEditStrategy(LiteralAutoEditStrategyTest.prefStore)) {

  import LiteralAutoEditStrategyTest._
  import scala.tools.eclipse.properties.EditorPreferencePage._

  @Before
  def startUp() {
    enable(P_ENABLE_AUTO_ESCAPE_SIGN, true)
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, true)
  }

  @Test
  def auto_close_string_literal() {
    test(input = "^", expectedOutput = "\"^\"", operation = Add("\""))
  }

  @Test
  def remove_string_pair() {
    test(input = """ "^" """, expectedOutput = """ ^ """, operation = Remove("\""))
  }

  @Test
  def remove_string_pair_with_auto_remove_escaped_sign_disabled() {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
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
  def remove_character_pair_with_auto_remove_escaped_sign_disabled() {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
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
  def not_remove_escaped_character_literal_if_feature_deactivated() {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    test(input = """ '\'^' """, expectedOutput = """ '\^' """, operation = Remove("'"))
  }

  @Test
  def auto_escape_backslash() {
    test(input = """ '^' """, expectedOutput = """ '\\^' """, operation = Add("\\"))
  }

  @Test
  def no_auto_escape_backslash_if_feature_deactivated() {
    enable(P_ENABLE_AUTO_ESCAPE_SIGN, false)
    test(input = """ '^' """, expectedOutput = """ '\^' """, operation = Add("\\"))
  }

  @Test
  def no_auto_escape_backslash_on_preceding_backslash() {
    test(input = """ '\^' """, expectedOutput = """ '\\^' """, operation = Add("\\"))
  }

  @Test
  def remove_escape_backslash() {
    test(input = """ '\\^' """, expectedOutput = """ '^' """, operation = Remove("\\"))
  }

  @Test
  def not_remove_escape_backslash_if_feature_deactivated() {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    test(input = """ '\\^' """, expectedOutput = """ '\^' """, operation = Remove("\\"))
  }

  @Test
  def remove_whole_character_literal() {
    test(input = """ '''^ """, expectedOutput = """ ^ """, operation = Remove("'''"))
  }

  @Test
  def remove_escaped_sign() {
    """btnfr"'\""" foreach { c =>
      test(input = """ '\%c^' """ format c, expectedOutput = """ '^' """, operation = Remove(c.toString))
    }
  }

  @Test
  def not_remove_escaped_sign_if_feature_deactivated() {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    """btnfr"'\""" foreach { c =>
      test(input = """ '\%c^' """ format c, expectedOutput = """ '\^' """, operation = Remove(c.toString))
    }
  }

  @Test
  def remove_escaped_sign_with_caret_on_backslash() {
    """btnfr"'\""" foreach { c =>
      test(input = """ '\^%c' """ format c, expectedOutput = """ '^' """, operation = Remove("\\"))
    }
  }

  @Test
  def not_remove_escaped_sign_on_backslash_if_feature_deactivated() {
    enable(P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN, false)
    """btnfr"'\""" foreach { c =>
      test(input = """ '\^%c' """ format c, expectedOutput = """ '^%c' """ format c, operation = Remove("\\"))
    }
  }

  @Test
  def not_remove_escaped_char_literal_if_it_is_not_terminated() {
    test(input = """ '\^'; """, expectedOutput = """ '^'; """, operation = Remove("\\"))
  }

  @Test
  def not_remove_escaped_char_literal_if_it_is_not_terminated_and_last_sign_is_the_literal() {
    test(input = """ '\^'""", expectedOutput = """ '^'""", operation = Remove("\\"))
  }

  @Test
  def not_add_escaped_char_literal_if_it_is_not_terminated() {
    test(input = """ 'n^ """, expectedOutput = """ 'n'^ """, operation = Add("'"))
  }

  // the following tests belong to multi-line string literals

  @Test
  def auto_close_multi_line_string_literal() {
    test(input = " \"\"^ ", expectedOutput = " \"\"\"^\"\"\" ", operation = Add("\""))
  }
}