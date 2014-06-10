package org.scalaide.core.ui

import org.eclipse.jface.text.DocumentCommand
import org.junit.Before
import org.junit.Test
import org.scalaide.ui.internal.editor.autoedits.SmartInsertionLogic
import org.scalaide.ui.internal.preferences.EditorPreferencePage._

class SmartInsertionAutoEditStrategyTest extends AutoEditStrategyTests {

  outer =>

  val strategy = new SmartInsertionLogic {
    def prefStore = outer.prefStore
    def registerTextEdit(cmd: DocumentCommand, caretPos: Int) = ()
  }
  val semicolon = Add(";")

  @Before
  def startUp() = {
    enable(P_ENABLE_SMART_INSERTION_SEMICOLONS, true)
  }

  @Test
  def no_smart_insertion_when_feature_disabled() = {
    enable(P_ENABLE_SMART_INSERTION_SEMICOLONS, false)
    "val x^ = 0" becomes "val x;^ = 0" after semicolon
  }

  @Test
  def move_semicolon_to_end() =
    "val x^ = 0" becomes "val x = 0;^" after semicolon

  @Test
  def not_move_semicolon_wen_it_is_already_at_the_end() =
    "val x = 0^" becomes "val x = 0;^" after semicolon

  @Test
  def not_move_semicolon_to_end_when_it_already_exists() =
    "val x^ = 0;" becomes "val x;^ = 0;" after semicolon

  @Test
  def not_move_semicolon_to_end_in_for_comprehension() =
    "for (i <- List(1)^)" becomes "for (i <- List(1);^)" after semicolon

  @Test
  def not_move_semicolon_to_end_in_for_comprehension_with_braces() =
    "for {i <- List(1)^}" becomes "for {i <- List(1);^}" after semicolon

  @Test
  def not_move_semicolon_to_end_in_for_comprehension_with_yield() =
    "for (i <- List(1)^) yield i" becomes "for (i <- List(1);^) yield i" after semicolon

  @Test
  def not_move_semicolon_to_end_in_nested_for_comprehension() =
    "for (i <- List(1)^; j <- List(1)) yield j" becomes "for (i <- List(1);^; j <- List(1)) yield j" after semicolon

  @Test
  def move_semicolon_to_end_before_for_comprehension() =
    "^for (i <- List(1)) yield i" becomes "for (i <- List(1)) yield i;^" after semicolon

}