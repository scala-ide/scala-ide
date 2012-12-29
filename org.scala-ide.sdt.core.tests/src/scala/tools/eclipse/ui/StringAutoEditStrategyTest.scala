package scala.tools.eclipse.ui

import scala.tools.eclipse.properties.EditorPreferencePage

import org.eclipse.jface.preference.IPreferenceStore
import org.junit.{ Before, Test }
import org.mockito.Mockito._

object StringAutoEditStrategyTest {

  val prefStore = mock(classOf[IPreferenceStore])

  def enableAutoEscape(enable: Boolean) {
    when(prefStore.getBoolean(EditorPreferencePage.P_ENABLE_AUTO_ESCAPE_LITERALS)).thenReturn(enable)
  }
}

class StringAutoEditStrategyTest extends AutoEditStrategyTests(
    new StringAutoEditStrategy(StringAutoEditStrategyTest.prefStore)) {

  @Before
  def startUp() {
    StringAutoEditStrategyTest.enableAutoEscape(true)
  }

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
  def no_auto_escape_string_literal_if_feature_deactivated() {
    StringAutoEditStrategyTest.enableAutoEscape(false)
    test(input = """ "^ " """, expectedOutput = """ ""^ " """, operation = Add("\""))
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
  def no_auto_escape_string_on_double_preceding_backslash_if_feature_deactivated() {
    StringAutoEditStrategyTest.enableAutoEscape(false)
    test(input = """ "\\^ " """, expectedOutput = """ "\\"^ " """, operation = Add("\""))
  }

  @Test
  def remove_escaped_string_literal() {
    test(input = """ "\"^" """, expectedOutput = """ "^" """, operation = Remove("\""))
  }

  @Test
  def not_remove_escaped_string_literal_if_feature_deactivated() {
    StringAutoEditStrategyTest.enableAutoEscape(false)
    test(input = """ "\"^" """, expectedOutput = """ "\^" """, operation = Remove("\""))
  }

  @Test
  def auto_escape_backslash() {
    test(input = """ "^" """, expectedOutput = """ "\\^" """, operation = Add("\\"))
  }

  @Test
  def no_auto_escape_backslash_if_feature_deactivated() {
    StringAutoEditStrategyTest.enableAutoEscape(false)
    test(input = """ "^" """, expectedOutput = """ "\^" """, operation = Add("\\"))
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
  def no_auto_escape_backslash_on_double_preceding_backslash_if_feature_deactivated() {
    StringAutoEditStrategyTest.enableAutoEscape(false)
    test(input = """ "\\^ " """, expectedOutput = """ "\\\^ " """, operation = Add("\\"))
  }

  @Test
  def remove_escape_backslash() {
    test(input = """ "\\^" """, expectedOutput = """ "^" """, operation = Remove("\\"))
  }

  @Test
  def not_remove_escape_backslash_if_feature_deactivated() {
    StringAutoEditStrategyTest.enableAutoEscape(false)
    test(input = """ "\\^" """, expectedOutput = """ "\^" """, operation = Remove("\\"))
  }

}