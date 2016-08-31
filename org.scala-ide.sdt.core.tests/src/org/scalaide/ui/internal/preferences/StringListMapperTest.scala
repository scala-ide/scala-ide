package org.scalaide.ui.internal.preferences

import org.junit.Assert._
import org.junit.Test

class StringListMapperTest {
  private def testWith(strs: String*): Unit = {
    val encoded = StringListMapper.encode(strs)
    val decoded = StringListMapper.decode(encoded)
    assertEquals(s"Error related to encoded representation '$encoded'", strs, decoded)
  }

  private def expectExceptionOnDecode(str: String): Unit = {
    try {
      StringListMapper.decode(str)
      throw new AssertionError(s"Expected IllegalArgumentException for input $str")
    } catch {
      case _: IllegalArgumentException => ()
    }
  }

  @Test
  def emptyList(): Unit = {
    testWith()
  }

  @Test
  def emptyString(): Unit = {
    testWith("")
  }

  @Test
  def singleString(): Unit = {
    testWith("single")
    testWith("0")
    testWith("1")
  }

  @Test
  def mulitpleStrings(): Unit = {
    testWith("a", "b")
    testWith("", "")
    testWith("", "", "")
    testWith("0", "0", "0")
    testWith("", "", "", "a", "b", "abc")
    testWith("0", "1", "   ", "a", "b", "abc", "\n", "12340")
  }

  @Test
  def withListOfEncodedLists(): Unit = {
    testWith(
        StringListMapper.encode(Seq()),
        StringListMapper.encode(Seq("")),
        StringListMapper.encode(Seq("", "1", "22")))
  }

  @Test
  def noStackOverflow(): Unit = {
    testWith(Seq.fill(10000)(""): _*)
  }

  @Test
  def testWithIllegalInput(): Unit = {
    expectExceptionOnDecode("1")
    expectExceptionOnDecode("2|a")
    expectExceptionOnDecode("1|1|2|1")
    expectExceptionOnDecode("asdf3|||")
    expectExceptionOnDecode("9|8|")
  }
}
