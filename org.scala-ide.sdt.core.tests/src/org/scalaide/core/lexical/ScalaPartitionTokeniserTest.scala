package org.scalaide.core.lexical

import scala.xml.Elem

import org.junit.Test
import org.scalaide.core.internal.lexical.ScalaPartitionRegion
import org.scalaide.core.internal.lexical.ScalaPartitionTokeniser
import org.scalaide.core.internal.lexical.ScalaPartitions._

class ScalaPartitionTokeniserTest {
  import ScalaPartitionTokeniserTest._

  @Test
  def bug2522() {
    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """def dev = <div class="menu">...</div>""" ==>
      ((SCALA_DEFAULT_CONTENT, 0, 9), (XML_TAG, 10, 27), (XML_PCDATA, 28, 30), (XML_TAG, 31, 36))
  }

  @Test
  def defaultContent() {
    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """package foo""" ==> ((SCALA_DEFAULT_CONTENT, 0, 10))
  }

  @Test
  def comments() {
    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """package /* comment */ foo // comment""" ==>
      ((SCALA_DEFAULT_CONTENT, 0, 7), (SCALA_MULTI_LINE_COMMENT, 8, 20), (SCALA_DEFAULT_CONTENT, 21, 25), (SCALA_SINGLE_LINE_COMMENT, 26, 35))

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """/* comment /* nested */ */""" ==>
      ((SCALA_MULTI_LINE_COMMENT, 0, 25))

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """/** comment /** nested **/ */""" ==>
      ((SCALADOC, 0, 28))

  }

  @Test
  def basicXml() {
    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """<foo/>""" ==> ((XML_TAG, 0, 5))

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """<![CDATA[ <foo/> ]]>""" ==> ((XML_CDATA, 0, 19))

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """<!-- comment -->""" ==> ((XML_COMMENT, 0, 15))

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """<?xml version='1.0' encoding='UTF-8'?>""" ==> ((XML_PI, 0, 37))
  }

  @Test
  def strings() {
    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    <t>"ordinary string"</t> ==> ((SCALA_STRING, 0, 16));

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    <t>"""scala multiline string"""</t> ==> ((SCALA_MULTI_LINE_STRING, 0, 27))
  }

  @Test
  def stringInterpolation() {
    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    <t>s"my name is $name"</t> ==>
      ((SCALA_DEFAULT_CONTENT, 0, 0), (SCALA_STRING, 1, 13), (SCALA_DEFAULT_CONTENT, 14, 17), (SCALA_STRING, 18, 18))

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    <t>s"""my name is $name"""</t> ==>
      ((SCALA_DEFAULT_CONTENT, 0, 0), (SCALA_MULTI_LINE_STRING, 1, 15), (SCALA_DEFAULT_CONTENT, 16, 19), (SCALA_MULTI_LINE_STRING, 20, 22))

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """s"my name is ${person.name}"""" ==>
      ((SCALA_DEFAULT_CONTENT, 0, 0), (SCALA_STRING, 1, 13), (SCALA_DEFAULT_CONTENT, 14, 26), (SCALA_STRING, 27, 27))

    // 0 0 00000001111111111222222222 2 3 33333333344444444445
    // 1 2 34567890123456789012345678 9 0 12345678901234567890
    "s\"\"\"my name is ${person.name}\"\"\"" ==>
      ((SCALA_DEFAULT_CONTENT, 0, 0), (SCALA_MULTI_LINE_STRING, 1, 15), (SCALA_DEFAULT_CONTENT, 16, 28), (SCALA_MULTI_LINE_STRING, 29, 31))

  }

  @Test
  def simple_scaladoc() {
    "/**doc*/" ==> ((SCALADOC, 0, 7))
  }

  @Test
  def scaladoc_with_normal_code() {
    "val i = 0; /**doc*/ val j = 0" ==>
      ((SCALA_DEFAULT_CONTENT, 0, 10), (SCALADOC, 11, 18), (SCALA_DEFAULT_CONTENT, 19, 28))
  }

  @Test
  def scaladoc_with_codeblock() {
    "/**{{{val i = 0}}}*/" ==>
      ((SCALADOC, 0, 2), (SCALADOC_CODE_BLOCK, 3, 17), (SCALADOC, 18, 19))
  }

  @Test
  def scaladoc_code_block_terminated_early() {
    """/**{{{ "abc" */ val i = 0""" ==>
      ((SCALADOC, 0, 2), (SCALADOC, 3, 14), (SCALA_DEFAULT_CONTENT, 15, 24))
  }

  @Test
  def scaladoc_after_invalid_code_block() {
    "/**}}}{{{*/" ==>
      ((SCALADOC, 0, 5), (SCALADOC, 6, 10))
  }

  @Test
  def scaladoc_code_block_with_second_code_block_start() {
    "/**{{{ {{{ }}}*/" ==>
      ((SCALADOC, 0, 2), (SCALADOC_CODE_BLOCK, 3, 13), (SCALADOC, 14, 15))
  }

  @Test
  def scaladoc_code_block_opening_after_another_block() {
    "/**{{{foo}}}{{{*/" ==>
      ((SCALADOC, 0, 2), (SCALADOC_CODE_BLOCK, 3, 11), (SCALADOC, 12, 16))
  }
  @Test
  def scaladoc_code_block_closing_after_another_block() {
    "/**{{{foo}}}}}}*/" ==>
      ((SCALADOC, 0, 2), (SCALADOC_CODE_BLOCK, 3, 11), (SCALADOC, 12, 16))
  }

  @Test
  def multiple_scaladoc_code_blocks() {
    "/**{{{foo}}}{{{foo}}}*/" ==>
      ((SCALADOC, 0, 2), (SCALADOC_CODE_BLOCK, 3, 11), (SCALADOC_CODE_BLOCK, 12, 20), (SCALADOC, 21, 22))
  }

  @Test
  def scaladoc_code_block_nested_in_multi_line_comment() {
    "/*/**{{{/**/" ==>
      ((SCALA_MULTI_LINE_COMMENT, 0, 11))
  }

  @Test
  def char_literal() {
    "'a'" ==> ((SCALA_CHARACTER, 0, 2))
  }

  @Test
  def char_literal_containing_escape_sequence() {
    """'\n'""" ==> ((SCALA_CHARACTER, 0, 3))
  }

  @Test
  def char_literal_containing_unicode_sequence() {
    "'\\u0000'" ==> ((SCALA_CHARACTER, 0, 7))
  }

  @Test
  def char_literal_containing_octal_sequence() {
    """'\123'""" ==> ((SCALA_CHARACTER, 0, 5))
  }

}

object ScalaPartitionTokeniserTest {
  import scala.language.implicitConversions
  implicit def string2PimpedString(from: String): PimpedString = new PimpedString(from)
  implicit def element2PimpedString(from: Elem): PimpedString = new PimpedString(from.text)

  class PimpedString(source: String) {
    def ==>(expectedPartitions: List[(String, Int, Int)]) {
      val actualPartitions = ScalaPartitionTokeniser.tokenise(source)
      val expected = expectedPartitions.map(ScalaPartitionRegion.tupled)
      if (actualPartitions != expected)
        throw new AssertionError("""Expected != Actual
          |Expected: %s
          |Actual:   %s""".stripMargin.format(expected, actualPartitions))
    }
    def ==>(expectedPartitions: (String, Int, Int)*) { this ==> expectedPartitions.toList }
  }

  def partitions(expectedPartitions: (String, Int, Int)*) = expectedPartitions.toList

}