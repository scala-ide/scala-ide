package org.scalaide.core.ui

import org.eclipse.jface.text.IDocumentExtension3
import org.junit.Before
import org.junit.Test
import org.scalaide.core.internal.formatter.FormatterPreferences._
import org.scalaide.ui.internal.editor.autoedits.MultiLineStringAutoIndentStrategy
import org.scalaide.ui.internal.preferences.EditorPreferencePage._

import scalariform.formatter.preferences._

class MultiLineStringAutoIndentStrategyTest extends AutoEditStrategyTests {

  val strategy = new MultiLineStringAutoIndentStrategy(IDocumentExtension3.DEFAULT_PARTITIONING, prefStore)

  implicit class ToMultiLineString(s: String) {
    def mls = s.replaceAll("```", "\"\"\"").replaceAll("""\\t""", "\t").stripMargin
  }

  val newline = Add("\n")
  val tab = Add("\t")

  @Before
  def startup(): Unit = {
    enable(P_ENABLE_AUTO_INDENT_MULTI_LINE_STRING, true)
    enable(P_ENABLE_AUTO_STRIP_MARGIN_IN_MULTI_LINE_STRING, true)
    setIntPref(IndentSpaces.eclipseKey, 2)
    enable(IndentWithTabs.eclipseKey, false)
  }

  @Test
  def no_indent_when_feature_not_enabled() = disabled(P_ENABLE_AUTO_INDENT_MULTI_LINE_STRING) { """
    |val str = ```text^```
    |""".mls becomes """
    |val str = ```text
    |^```
    |""".mls after newline
  }

  @Test
  def one_indent_in_first_line() = """
    |val str = ```text^```
    |""".mls becomes """
    |val str = ```text
    |  ^```
    |""".mls after newline

  @Test
  def one_indent_in_first_line_with_text_after_cursor() = """
    |val str = ```^text```
    |""".mls becomes """
    |val str = ```
    |  ^text```
    |""".mls after newline

  @Test
  def indent_when_feature_enabled() = """
    |val str = ```text
    |    more text^```
    |""".mls becomes """
    |val str = ```text
    |    more text
    |    ^```
    |""".mls after newline

  @Test
  def indent_after_blank_line() = """
    |val str = ```text
    |    ^```
    |""".mls becomes """
    |val str = ```text
    |    $
    |    ^```
    |""".mls after newline

  @Test
  def no_extra_indent_on_tab_when_feature_not_enabled() = disabled(P_ENABLE_AUTO_INDENT_MULTI_LINE_STRING) { """
    |val str = ```text
    |    more text
    |^```
    |""".mls becomes """
    |val str = ```text
    |    more text
    |  ^```
    |""".mls after tab
  }

  @Test
  def indent_to_same_depth_as_previous_line_when_tab_is_hit() = """
    |val str = ```text
    |    more text
    |^
    |```
    |""".mls becomes """
    |val str = ```text
    |    more text
    |    ^
    |```
    |""".mls after tab

  @Test
  def indent_to_same_depth_when_previous_line_contains_tabs() = """
    |val str = ```text
    |\t\tmore text
    |    ^
    |```
    |""".mls becomes """
    |val str = ```text
    |\t\tmore text
    |    ^
    |```
    |""".mls after tab

  @Test
  def indent_to_same_depth_as_previous_line_when_tab_is_hit_but_current_line_contains_whitespace() = """
    |val str = ```text
    |      more text
    |   ^
    |```
    |""".mls becomes """
    |val str = ```text
    |      more text
    |      ^
    |```
    |""".mls after tab

  @Test
  def normal_tab_indent_when_current_line_contains_non_whitespace_text() = """
    |val str = ```text
    |      more text
    |  h^```
    |""".mls becomes """
    |val str = ```text
    |      more text
    |  h  ^```
    |""".mls after tab

  @Test
  def normal_tab_indent_when_previous_line_indent_depth_is_equal_to_current_line_indent_depth() = """
    |val str = ```text
    |more text
    |^```
    |""".mls becomes """
    |val str = ```text
    |more text
    |  ^```
    |""".mls after tab

  @Test
  def normal_tab_indent_when_previous_line_indent_depth_is_lower_than_current_line_indent_depth() = """
    |val str = ```text
    |more text
    |  ^```
    |""".mls becomes """
    |val str = ```text
    |more text
    |    ^```
    |""".mls after tab

  @Test
  def indent_with_tabs_when_smart_indent_disabled() = {
    enable(P_ENABLE_AUTO_INDENT_MULTI_LINE_STRING, false)
    enable(IndentWithTabs.eclipseKey, true)

    """
    |class X {
    |  val str = ```
    |^
    |  ```
    |}
    |""".mls becomes """
    |class X {
    |  val str = ```
    |\t^
    |  ```
    |}
    |""".mls after tab
  }

  @Test
  def add_no_strip_margin_when_auto_indent_is_disabled_while_strip_margin_feature_is_enabled() = disabled(P_ENABLE_AUTO_INDENT_MULTI_LINE_STRING) { """
    |val str = ```|text^```
    |""".mls becomes """
    |val str = ```|text
    |^```
    |""".mls after newline
  }

  @Test
  def add_no_strip_margin_when_feature_disabled() = disabled(P_ENABLE_AUTO_STRIP_MARGIN_IN_MULTI_LINE_STRING) { """
    |val str = ```|text^```
    |""".mls becomes """
    |val str = ```|text
    |  ^```
    |""".mls after newline
  }

  @Test
  def add_no_strip_margin_when_feature_disabled_but_strip_margin_exists() = disabled(P_ENABLE_AUTO_STRIP_MARGIN_IN_MULTI_LINE_STRING) { """
    |val str = ```|text
    |             |^
    |             |```.stripMargin
    |""".mls becomes """
    |val str = ```|text
    |             |
    |             ^
    |             |```.stripMargin
    |""".mls after newline
  }

  @Test
  def add_no_strip_margin_when_it_already_exists() = """
    |val str = ```|text^
    |             |```.stripMargin
    |""".mls becomes """
    |val str = ```|text
    |             |^
    |             |```.stripMargin
    |""".mls after newline

  @Test
  def add_no_strip_margin_when_it_already_exists_but_as_postfix_operator() = """
    |val str = ```|text^
    |             |``` stripMargin
    |""".mls becomes """
    |val str = ```|text
    |             |^
    |             |``` stripMargin
    |""".mls after newline

  @Test
  def add_no_strip_margin_call_when_multi_line_string_is_unclosed() = """
    |val str = ```|text^
    |""".mls becomes """
    |val str = ```|text
    |             |^
    |""".mls after newline

  @Test
  def add_no_strip_margin_call_when_multi_line_string_is_closed_and_cursor_not_in_first_line() = """
    |val str = ```|text
    |             |hello^
    |```
    |""".mls becomes """
    |val str = ```|text
    |             |hello
    |             ^
    |```
    |""".mls after newline

  @Test
  def indent_strip_margin_and_the_cursor_to_previous_indent_depth_in_first_line() = """
    |val str = ```|    text^```
    |""".mls becomes """
    |val str = ```|    text
    |             |    ^```.stripMargin
    |""".mls after newline

  @Test
  def indent_strip_margin_and_the_cursor_to_previous_indent_depth() = """
    |val str = ```|text
    |             |    more text^
    |             |```.stripMargin
    |""".mls becomes """
    |val str = ```|text
    |             |    more text
    |             |    ^
    |             |```.stripMargin
    |""".mls after newline

  @Test
  def indent_should_work_when_it_is_surrounded_by_other_code_parts() = """
    |class X {
    |  val str = ```text
    |         more text^
    |  ```
    |  def f = 0
    |}
    |""".mls becomes """
    |class X {
    |  val str = ```text
    |         more text
    |         ^
    |  ```
    |  def f = 0
    |}
    |""".mls after newline

  @Test
  def strip_margin_should_work_when_it_is_surrounded_by_other_code_parts() = """
    |class X {
    |  val str = ```|hello^```
    |  def f = 0
    |}
    |""".mls becomes """
    |class X {
    |  val str = ```|hello
    |               |^```.stripMargin
    |  def f = 0
    |}
    |""".mls after newline
}