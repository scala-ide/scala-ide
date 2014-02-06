package org.scalaide.core.ui

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocumentExtension3
import org.junit.Test
import org.mockito.Mockito._
import org.scalaide.ui.internal.editor.autoedits.MultiLineStringAutoEditStrategy

object MultiLineStringAutoEditStrategyTest {

  val prefStore = mock(classOf[IPreferenceStore])
}

class MultiLineStringAutoEditStrategyTest extends AutoEditStrategyTests(
    new MultiLineStringAutoEditStrategy(
        IDocumentExtension3.DEFAULT_PARTITIONING,
        MultiLineStringAutoEditStrategyTest.prefStore)) {

  @Test
  def remove_multi_line_string() {
    test(input = " \"\"\"^\"\"\" ", expectedOutput = " \"^\" ", operation = Remove("\""))
  }

  @Test
  def remove_non_empty_multi_line_string() {
    test(input = " \"\"\"^x\"\"\" ", expectedOutput = " \"\"^x\"\"\" ", operation = Remove("\""))
  }

  @Test
  def jump_over_first_closing_apostrophe() {
    test(input = " \"\"\"^\"\"\" ", expectedOutput = " \"\"\"\"^\"\" ", operation = Add("\""))
  }

  @Test
  def jump_over_second_closing_apostrophe() {
    test(input = " \"\"\"\"^\"\" ", expectedOutput = " \"\"\"\"\"^\" ", operation = Add("\""))
  }

  @Test
  def jump_over_third_closing_apostrophe() {
    test(input = " \"\"\"\"\"^\" ", expectedOutput = " \"\"\"\"\"\"^ ", operation = Add("\""))
  }

  @Test
  def remove_first_closing_apostrophe() {
    test(input = " \"\"\"\"^ ", expectedOutput = " \"\"\"^ ", operation = Remove("\""))
  }

  @Test
  def remove_second_closing_apostrophe() {
    test(input = " \"\"\"\"\"^ ", expectedOutput = " \"\"\"\"^ ", operation = Remove("\""))
  }

  @Test
  def remove_third_closing_apostrophe() {
    test(input = " \"\"\"\"\"\"^ ", expectedOutput = " \"\"\"\"\"^ ", operation = Remove("\""))
  }
}