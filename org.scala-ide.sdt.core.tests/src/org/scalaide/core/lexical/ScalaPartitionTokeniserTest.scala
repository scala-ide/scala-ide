package org.scalaide.core.lexical

import scala.xml.Elem
import org.eclipse.jface.text.IDocument
import org.junit.Test
import org.junit.Before
import org.scalaide.core.internal.lexical.ScalaPartitions._
import org.eclipse.jface.text.IDocument.DEFAULT_CONTENT_TYPE
import org.eclipse.jdt.ui.text.IJavaPartitions._
import org.scalaide.core.internal.lexical.ScalaPartitionTokeniser
import org.scalaide.core.internal.lexical.ScalaPartitionRegion

class ScalaPartitionTokeniserTest {
  import ScalaPartitionTokeniserTest._

  @Test
  def bug2522() {
    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """def dev = <div class="menu">...</div>""" ==>
      ((DEFAULT_CONTENT_TYPE, 0, 9), (XML_TAG, 10, 27), (XML_PCDATA, 28, 30), (XML_TAG, 31, 36))
  }

  @Test
  def defaultContent() {
    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """package foo""" ==> ((DEFAULT_CONTENT_TYPE, 0, 10))
  }

  @Test
  def comments() {
    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """package /* comment */ foo // comment""" ==>
      ((DEFAULT_CONTENT_TYPE, 0, 7), (JAVA_MULTI_LINE_COMMENT, 8, 20), (DEFAULT_CONTENT_TYPE, 21, 25), (JAVA_SINGLE_LINE_COMMENT, 26, 35))

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """/* comment /* nested */ */""" ==>
      ((JAVA_MULTI_LINE_COMMENT, 0, 25))

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """/** comment /** nested **/ */""" ==>
      ((JAVA_DOC, 0, 28))

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
    <t>"ordinary string"</t> ==> ((JAVA_STRING, 0, 16));

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    <t>"""scala multiline string"""</t> ==> ((SCALA_MULTI_LINE_STRING, 0, 27))
  }

  @Test
  def stringInterpolation() {
    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    <t>s"my name is $name"</t> ==>
      ((DEFAULT_CONTENT_TYPE, 0, 0), (JAVA_STRING, 1, 13), (DEFAULT_CONTENT_TYPE, 14, 17), (JAVA_STRING, 18, 18))

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    <t>s"""my name is $name"""</t> ==>
      ((DEFAULT_CONTENT_TYPE, 0, 0), (SCALA_MULTI_LINE_STRING, 1, 15), (DEFAULT_CONTENT_TYPE, 16, 19), (SCALA_MULTI_LINE_STRING, 20, 22))

    // 000000000011111111112222222222333333333344444444445
    // 012345678901234567890123456789012345678901234567890
    """s"my name is ${person.name}"""" ==>
      ((DEFAULT_CONTENT_TYPE, 0, 0), (JAVA_STRING, 1, 13), (DEFAULT_CONTENT_TYPE, 14, 26), (JAVA_STRING, 27, 27))

    // 0 0 00000001111111111222222222 2 3 33333333344444444445
    // 1 2 34567890123456789012345678 9 0 12345678901234567890
    "s\"\"\"my name is ${person.name}\"\"\"" ==>
      ((DEFAULT_CONTENT_TYPE, 0, 0), (SCALA_MULTI_LINE_STRING, 1, 15), (DEFAULT_CONTENT_TYPE, 16, 28), (SCALA_MULTI_LINE_STRING, 29, 31))

  }

  @Test
  def simple_scaladoc() {
    "/**doc*/" ==> ((JAVA_DOC, 0, 7))
  }

  @Test
  def scaladoc_with_normal_code() {
    "val i = 0; /**doc*/ val j = 0" ==>
      ((DEFAULT_CONTENT_TYPE, 0, 10), (JAVA_DOC, 11, 18), (DEFAULT_CONTENT_TYPE, 19, 28))
  }

  @Test
  def scaladoc_with_codeblock() {
    "/**{{{val i = 0}}}*/" ==>
      ((JAVA_DOC, 0, 2), (SCALADOC_CODE_BLOCK, 3, 17), (JAVA_DOC, 18, 19))
  }

  @Test
  def scaladoc_code_block_terminated_early() {
    """/**{{{ "abc" */ val i = 0""" ==>
      ((JAVA_DOC, 0, 2), (JAVA_DOC, 3, 14), (DEFAULT_CONTENT_TYPE, 15, 24))
  }

  @Test
  def scaladoc_after_invalid_code_block() {
    "/**}}}{{{*/" ==>
      ((JAVA_DOC, 0, 5), (JAVA_DOC, 6, 10))
  }

  @Test
  def scaladoc_code_block_with_second_code_block_start() {
    "/**{{{ {{{ }}}*/" ==>
      ((JAVA_DOC, 0, 2), (SCALADOC_CODE_BLOCK, 3, 13), (JAVA_DOC, 14, 15))
  }

  @Test
  def scaladoc_code_block_opening_after_another_block() {
    "/**{{{foo}}}{{{*/" ==>
      ((JAVA_DOC, 0, 2), (SCALADOC_CODE_BLOCK, 3, 11), (JAVA_DOC, 12, 16))
  }
  @Test
  def scaladoc_code_block_closing_after_another_block() {
    "/**{{{foo}}}}}}*/" ==>
      ((JAVA_DOC, 0, 2), (SCALADOC_CODE_BLOCK, 3, 11), (JAVA_DOC, 12, 16))
  }

  @Test
  def multiple_scaladoc_code_blocks() {
    "/**{{{foo}}}{{{foo}}}*/" ==>
      ((JAVA_DOC, 0, 2), (SCALADOC_CODE_BLOCK, 3, 11), (SCALADOC_CODE_BLOCK, 12, 20), (JAVA_DOC, 21, 22))
  }

  @Test
  def scaladoc_code_block_nested_in_multi_line_comment() {
    "/*/**{{{/**/" ==>
      ((JAVA_MULTI_LINE_COMMENT, 0, 11))
  }

  @Test
  def char_literal() {
    "'a'" ==> ((JAVA_CHARACTER, 0, 2))
  }

  @Test
  def char_literal_containing_escape_sequence() {
    """'\n'""" ==> ((JAVA_CHARACTER, 0, 3))
  }

  @Test
  def char_literal_containing_unicode_sequence() {
    "'\\u0000'" ==> ((JAVA_CHARACTER, 0, 7))
  }

  @Test
  def char_literal_containing_octal_sequence() {
    """'\123'""" ==> ((JAVA_CHARACTER, 0, 5))
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