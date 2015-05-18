package org.scalaide.core.hyperlink

import org.scalaide.util.ScalaWordFinder
import org.eclipse.jface.text.IRegion
import org.junit.Test
import org.junit.Assert
import org.eclipse.jface.text.Region

class ScalaWordFinderTest {
  private def findWord(doc: String): IRegion = {
    val pos = doc.indexOf('|')
    val filteredDoc = doc.filterNot(_ == '|')
    ScalaWordFinder.findWord(filteredDoc.toIndexedSeq, pos)
  }

  private def test(doc: String, r: (Int, Int)): Unit = {
    val range = findWord(doc)
    val expected = new Region(r._1, r._2)
    Assert.assertEquals(s"Unexpected region for $doc", expected, range)
  }

  @Test
  def atEndOfDoc(): Unit = {
    test("word|", (0, 4))
    test("|word", (0, 4))
  }

  @Test
  def middleWords(): Unit = {
    test("wor|d", (0, 4))
    test("w|ord", (0, 4))
    test(" wor|d  ", (1, 4))
  }

  @Test
  def delimiters(): Unit = {
    test("ref.sel|ection", (4, 9))
    test("ref.sel|ection.other", (4, 9))
  }

  @Test
  def delimitersArg(): Unit = {
    test("ref(arg|ument", (4, 8))
    test("ref(|argument)", (4, 8))
    test("ref(argument|)", (4, 8))
    test("ref|(argument)", (0, 3))
  }

  @Test
  def zeroLen(): Unit = {
    test("|", (0, 0))
    test("ref(|)", (4, 0))
    test("ref.|", (4, 0))
  }

  @Test
  def interpolated(): Unit = {
    test(""" s"?fo|o" """.replace('?', '$'), (4, 3))
    test(""" s"?{fo|o}" """.replace('?', '$'), (5, 3))
    test(""" s"?{|foo}" """.replace('?', '$'), (5, 3))
    test(""" s"?{foo|}" """.replace('?', '$'), (5, 3))
  }

  @Test
  def middleOperators(): Unit = {
    test("==|==", (0, 4))
    test("=|-~/", (0, 4))
    test(" <|<<=  ", (1, 4))
  }

  @Test
  def delimitersAndOperators(): Unit = {
    test("foo(==|==)", (4, 4))
    test("foo(|=-~/)", (4, 4))
    test("foo(<<<=|)  ", (4, 4))
    test("foo(<<<=|", (4, 4))
  }
}
