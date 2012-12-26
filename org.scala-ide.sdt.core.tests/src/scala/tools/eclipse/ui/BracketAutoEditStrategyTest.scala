package scala.tools.eclipse.ui

import scala.tools.eclipse.properties.EditorPreferencePage

import org.eclipse.jface.preference.IPreferenceStore
import org.junit.{ Before, Test }
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
  def prevent_auto_closing_brace_when_editing_an_existing_line() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
    test(
        input = "List(1) map^ (_+1)",
        expectedOutput = "List(1) map{^ (_+1)",
        operation = Add("{"))
  }

  @Test
  def not_prevent_auto_closing_brace_when_editing_an_existing_line() {
    test(
        input = "List(1) map^ (_+1)",
        expectedOutput = "List(1) map{^} (_+1)",
        operation = Add("{"))
  }

  @Test
  def auto_closing_brace_in_function_literal() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
    test(
        input = "List(List(1)) map { _ map ^ }",
        expectedOutput = "List(List(1)) map { _ map {^} }",
        operation = Add("{"))
  }

  @Test
  def auto_closing_brace_in_and_before_another_function_literal() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
    test(
        input = "List(List(1)) map { _ map^ (_+1) }",
        expectedOutput = "List(List(1)) map { _ map{^} (_+1) }",
        operation = Add("{"))
  }

  @Test
  def auto_closing_brace_before_trailing_whitespaces() {
    BracketAutoEditStrategyTest.enableAutoClosing(false)
    test(input = "^   ", expectedOutput = "{^}   ", operation = Add("{"))
  }
}