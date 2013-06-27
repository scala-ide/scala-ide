package scala.tools.eclipse.ui

import scala.tools.eclipse.properties.EditorPreferencePage

import org.eclipse.jface.preference.IPreferenceStore
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito._

object BracketAutoEditStrategyTest {

  val prefStore = mock(classOf[IPreferenceStore])

  def enableAutoClosing(enable: Boolean) {
    when(prefStore.getBoolean(EditorPreferencePage.P_ENABLE_AUTO_CLOSING_BRACES)).thenReturn(enable)
  }
}

class BracketAutoEditStrategyTest extends AutoEditStrategyTests(
    new BracketAutoEditStrategy(BracketAutoEditStrategyTest.prefStore)) {

  @Before
  def startUp() {
    BracketAutoEditStrategyTest.enableAutoClosing(true)
  }

  @Test
  def auto_closing_opening_brace() {
    test(input = "^", expectedOutput = "{^}", operation = Add("{"))
  }

  @Test
  def auto_closing_opening_brace_on_disabled_feature() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
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

  @Test
  def auto_closing_nested_opening_brace() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
    test(input = "{^}", expectedOutput = "{{^}}", operation = Add("{"))
  }

  @Test
  def auto_closing_brace_after_pending_closing_brace() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
    test(input = "} map {^}", expectedOutput = "} map {{^}}", operation = Add("{"))
  }

  @Test
  def prevent_auto_closing_brace_when_caret_before_non_white_space() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
    test(
        input = "List(1) map ^(_+1)",
        expectedOutput = "List(1) map {^(_+1)",
        operation = Add("{"))
  }

  @Test
  def not_prevent_auto_closing_brace_when_caret_non_white_space() {
    test(
        input = "List(1) map ^(_+1)",
        expectedOutput = "List(1) map {^}(_+1)",
        operation = Add("{"))
  }

  @Test
  def auto_closing_brace_when_caret_before_white_space() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
    test(
        input = "List(1) map^ (_+1)",
        expectedOutput = "List(1) map{^} (_+1)",
        operation = Add("{"))
  }

  @Test
  def auto_closing_brace_before_white_space() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
    test(input = "^   ", expectedOutput = "{^}   ", operation = Add("{"))
  }

  @Test
  def no_auto_closing_brace_on_missing_opening_bracket() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
    test(
        input = "List(1) map {^}}",
        expectedOutput = "List(1) map {{^}}",
        operation = Add("{"))
  }

  @Test
  def auto_closing_brace_before_matching_braces() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
    test(
        input = "List(1) map {^{}}",
        expectedOutput = "List(1) map {{^}{}}",
        operation = Add("{"))
  }
}