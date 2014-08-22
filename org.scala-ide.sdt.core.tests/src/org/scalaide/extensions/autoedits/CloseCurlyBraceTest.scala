package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class CloseCurlyBraceTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new CloseCurlyBrace {
    override val document = doc
    override val textChange = change
  }

  val curlyBrace = Add("{")

  @Test
  def auto_closing_opening_brace() =
    "^" becomes "{[[]]}^" after curlyBrace

  @Test
  def auto_closing_nested_opening_brace() =
    "{^}" becomes "{{[[]]}^}" after curlyBrace

  @Test
  def auto_closing_brace_after_pending_closing_brace() =
    "} map {^}" becomes "} map {{[[]]}^}" after curlyBrace

  @Test
  def prevent_auto_closing_brace_when_caret_before_non_white_space() =
    "List(1) map ^(_+1)" becomes "List(1) map {^(_+1)" after curlyBrace

  @Test
  def auto_closing_brace_when_caret_before_white_space() =
    "List(1) map^ (_+1)" becomes "List(1) map{[[]]}^ (_+1)" after curlyBrace

  @Test
  def auto_closing_brace_before_white_space() =
    "^   " becomes "{[[]]}^   " after curlyBrace

  @Test
  def no_auto_closing_brace_on_missing_opening_bracket() =
    "List(1) map {^}}" becomes "List(1) map {{^}}" after curlyBrace
}
