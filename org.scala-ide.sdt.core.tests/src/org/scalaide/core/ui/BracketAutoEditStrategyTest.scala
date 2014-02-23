package org.scalaide.core.ui

import org.junit.Before
import org.junit.Test
import org.scalaide.ui.internal.editor.autoedits.BracketAutoEditStrategy
import org.scalaide.ui.internal.preferences.EditorPreferencePage._

import AutoEditStrategyTests._

class BracketAutoEditStrategyTest extends AutoEditStrategyTests(
    new BracketAutoEditStrategy(prefStore)) {

  @Before
  def startUp() {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, true)
  }

  @Test
  def auto_closing_opening_brace() {
    test(input = "^", expectedOutput = "{^}", operation = Add("{"))
  }

  @Test
  def auto_closing_opening_brace_on_disabled_feature() {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
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
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(input = "{^}", expectedOutput = "{{^}}", operation = Add("{"))
  }

  @Test
  def auto_closing_brace_after_pending_closing_brace() {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(input = "} map {^}", expectedOutput = "} map {{^}}", operation = Add("{"))
  }

  @Test
  def prevent_auto_closing_brace_when_caret_before_non_white_space() {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
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
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(
        input = "List(1) map^ (_+1)",
        expectedOutput = "List(1) map{^} (_+1)",
        operation = Add("{"))
  }

  @Test
  def auto_closing_brace_before_white_space() {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(input = "^   ", expectedOutput = "{^}   ", operation = Add("{"))
  }

  @Test
  def no_auto_closing_brace_on_missing_opening_bracket() {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(
        input = "List(1) map {^}}",
        expectedOutput = "List(1) map {{^}}",
        operation = Add("{"))
  }

  @Test
  def auto_closing_brace_before_matching_braces() {
    enable(P_ENABLE_AUTO_CLOSING_BRACES, false)
    test(
        input = "List(1) map {^{}}",
        expectedOutput = "List(1) map {{^}{}}",
        operation = Add("{"))
  }
}