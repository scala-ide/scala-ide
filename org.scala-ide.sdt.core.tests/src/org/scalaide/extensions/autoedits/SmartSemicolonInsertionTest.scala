package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.junit.Test

class SmartSemicolonInsertionTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange): AutoEdit = new SmartSemicolonInsertion {
    override val document = doc
    override val textChange = change
  }

  val semicolon = Add(";")

  @Test
  def move_semicolon_to_end() =
    "val x^ = 0" becomes "val x = 0;^" after semicolon

  @Test
  def move_semicolon_to_end_in_multi_line_string() = """
    class X {
      val x^ = 0
    }""" becomes """
    class X {
      val x = 0;^
    }""" after semicolon

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
