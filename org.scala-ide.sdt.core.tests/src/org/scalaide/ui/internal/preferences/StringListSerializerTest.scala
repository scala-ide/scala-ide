package org.scalaide.ui.internal.preferences

import org.junit.Assert._
import org.junit.Test

class StringListSerializerTest {
  private def testWith(strs: String*): Unit = {
    val serialized = StringListSerializer.serialize(strs)
    val deserialized = StringListSerializer.deserialize(serialized)
    assertEquals(s"Error related to serialized representation '$serialized'", strs, deserialized)
  }

  private def expectExceptionOnDeserialize(str: String): Unit = {
    try {
      StringListSerializer.deserialize(str)
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
  def withListOfSerializedLists(): Unit = {
    testWith(
        StringListSerializer.serialize(Seq()),
        StringListSerializer.serialize(Seq("")),
        StringListSerializer.serialize(Seq("", "1", "22")))
  }

  @Test
  def noStackOverflow(): Unit = {
    testWith(Seq.fill(10000)(""): _*)
  }

  @Test
  def testWithIllegalInput(): Unit = {
    expectExceptionOnDeserialize("1")
    expectExceptionOnDeserialize("2|a")
    expectExceptionOnDeserialize("1|1|2|1")
    expectExceptionOnDeserialize("asdf3|||")
    expectExceptionOnDeserialize("9|8|")
  }
}
