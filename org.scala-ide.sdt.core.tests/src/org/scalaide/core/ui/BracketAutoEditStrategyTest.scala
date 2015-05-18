package org.scalaide.core.ui

import org.junit.Before
import org.junit.Test
import org.scalaide.ui.internal.editor.autoedits.BracketAutoEditStrategy
import org.scalaide.ui.internal.preferences.EditorPreferencePage._

class BracketAutoEditStrategyTest extends AutoEditStrategyTests {

  val strategy = new BracketAutoEditStrategy(prefStore)

  @Before
  def startUp(): Unit = {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, true)
  }

  @Test
  def auto_closing_opening_brace(): Unit = {
    test(input = "^", expectedOutput = "{^}", operation = Add("{"))
  }

  @Test
  def auto_closing_opening_brace_on_disabled_feature(): Unit = {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(input = "^", expectedOutput = "{^}", operation = Add("{"))
  }

  @Test
  def remove_brace_pair(): Unit = {
    test(input = "{^}", expectedOutput = "^", operation = Remove("{"))
  }

  @Test
  def remove_into_brace_pair(): Unit = {
    test(input = "{}abc^", expectedOutput = "{^", operation = Remove("}abc"))
  }

  @Test
  def jump_over_closing_brace(): Unit = {
    test(input = "}^}", expectedOutput = "}}^", operation = Add("}"))
  }

  @Test
  def add_closing_brace(): Unit = {
    test(input = "^", expectedOutput = "}^", operation = Add("}"))
  }

  @Test
  def remove_opening_brace(): Unit = {
    test(input = "{^", expectedOutput = "^", operation = Remove("{"))
  }

  @Test
  def auto_closing_nested_opening_brace(): Unit = {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(input = "{^}", expectedOutput = "{{^}}", operation = Add("{"))
  }

  @Test
  def auto_closing_brace_after_pending_closing_brace(): Unit = {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(input = "} map {^}", expectedOutput = "} map {{^}}", operation = Add("{"))
  }

  @Test
  def prevent_auto_closing_brace_when_caret_before_non_white_space(): Unit = {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(
        input = "List(1) map ^(_+1)",
        expectedOutput = "List(1) map {^(_+1)",
        operation = Add("{"))
  }

  @Test
  def not_prevent_auto_closing_brace_when_caret_non_white_space(): Unit = {
    test(
        input = "List(1) map ^(_+1)",
        expectedOutput = "List(1) map {^}(_+1)",
        operation = Add("{"))
  }

  @Test
  def auto_closing_brace_when_caret_before_white_space(): Unit = {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(
        input = "List(1) map^ (_+1)",
        expectedOutput = "List(1) map{^} (_+1)",
        operation = Add("{"))
  }

  @Test
  def auto_closing_brace_before_white_space(): Unit = {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(input = "^   ", expectedOutput = "{^}   ", operation = Add("{"))
  }

  @Test
  def no_auto_closing_brace_on_missing_opening_bracket(): Unit = {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(
        input = "List(1) map {^}}",
        expectedOutput = "List(1) map {{^}}",
        operation = Add("{"))
  }

  @Test
  def auto_closing_brace_before_matching_braces(): Unit = {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(
        input = "List(1) map {^{}}",
        expectedOutput = "List(1) map {{^}{}}",
        operation = Add("{"))
  }
}