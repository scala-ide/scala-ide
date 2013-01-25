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
}

class LiteralAutoEditStrategyTest extends AutoEditStrategyTests(
    new LiteralAutoEditStrategy(LiteralAutoEditStrategyTest.prefStore)) {

  @Before
  def startUp() {
    LiteralAutoEditStrategyTest.enableAutoEscape(true)
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
  def not_remove_escaped_character_literal_if_feature_deactivated() {
    LiteralAutoEditStrategyTest.enableAutoEscape(false)
    test(input = """ '\'^' """, expectedOutput = """ '\^' """, operation = Remove("'"))
  }

  @Test
  def auto_escape_backslash() {
    test(input = """ '^' """, expectedOutput = """ '\\^' """, operation = Add("\\"))
  }

  @Test
  def no_auto_escape_backslash_if_feature_deactivated() {
    LiteralAutoEditStrategyTest.enableAutoEscape(false)
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
    LiteralAutoEditStrategyTest.enableAutoEscape(false)
    test(input = """ '\\^' """, expectedOutput = """ '\^' """, operation = Remove("\\"))
  }

  @Test
  def remove_whole_character_literal() {
    test(input = """ '''^ """, expectedOutput = """ ^ """, operation = Remove("'''"))
  }
}