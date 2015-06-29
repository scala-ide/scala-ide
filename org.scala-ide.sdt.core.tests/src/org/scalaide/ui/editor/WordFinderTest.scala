package org.scalaide.ui.editor

import org.junit.Test
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.Document
import org.junit.Assert

class WordFinderTest {

  private final val CaretMarker = '|'

  private def document(text: String) = new {
    def shouldFind(expectedWord: String): Unit = {
      val caret: Int = {
        val offset = text.indexOf(CaretMarker)
        if (offset == -1) Assert.fail("Could not locate caret position marker '*' in test.")
        offset
      }
      val cleanedText = text.filterNot(_ == CaretMarker).mkString
      val doc = new Document(cleanedText)
      val region = WordFinder.findWord(doc, caret)
      val actualWord = doc.get(region.getOffset(), region.getLength())
      Assert.assertEquals("The word at the caret position.", expectedWord, actualWord)
    }
  }

  @Test
  def findWord_whenCaretIsInTheMiddle(): Unit = {
    document {
      "necess|ary"
    } shouldFind ("necessary")
  }

  @Test
  def findWord_whenCaretIsAtTheEnd(): Unit = {
    document {
      "necessary|"
    } shouldFind ("necessary")
  }

  @Test
  def findWord_whenCaretIsAtTheBeginning(): Unit = {
    document {
      "|necessary"
    } shouldFind ("necessary")
  }

  @Test
  def noWord_whenCaretIsSurroundedByWhitespaces(): Unit = {
    document {
      "it is | necessary"
    } shouldFind ("")
  }

  @Test
  def noWord_whenCaretIsAtTheEndAfterWhitespace(): Unit = {
    document {
      "necessary |"
    } shouldFind ("")
  }
}