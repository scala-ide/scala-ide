package org.scalaide.core.text

import org.junit.ComparisonFailure
import org.junit.Test
import org.eclipse.jface.text.{Document => EDocument}
import org.scalaide.core.internal.text.TextDocument

class DocumentTest {

  type Op = Document => String

  final def document(text: String): Document =
    new TextDocument(new EDocument(text))

  final implicit class Assert_===[A](actual: A) {
    def ===(expected: A): Unit =
      if (actual != expected)
        throw new ComparisonFailure("", expected.toString(), actual.toString())
  }

  final implicit class StringAsTest(input: String) {
    def becomes(expectedOutput: String) =
      document(input) -> expectedOutput
  }
  final implicit class TestExecutor(testData: (Document, String)) {
    def after(operation: Op) = test(testData._1, testData._2, operation)
  }

  final def test(doc: Document, expectedOutput: String, operation: Op): Unit = {
    val actual = operation(doc)
    actual === expectedOutput
  }

  @Test
  def length_returns_the_length() = {
    val d = document("hello world")
    d.length === 11
  }

  @Test
  def text_returns_string() =
    "hello world" becomes "hello world" after (_.text)

  @Test
  def textRange_returns_range() =
    "hello world" becomes "lo wo" after (_.textRange(3, 8))

  @Test(expected = classOf[IndexOutOfBoundsException])
  def textRange_throws_when_start_lower_than_zero() = {
    val d = document("hello world")
    d.textRange(-1, 8)
    ()
  }

  @Test(expected = classOf[IndexOutOfBoundsException])
  def textRange_throws_when_end_lower_than_start() = {
    val d = document("hello world")
    d.textRange(5, 4)
    ()
  }

  @Test(expected = classOf[IndexOutOfBoundsException])
  def textRange_throws_when_end_greater_than_length() = {
    val d = document("hello world")
    d.textRange(5, 20)
    ()
  }

  @Test
  def textRangeOpt_returns_option() = {
    val d = document("hello world")
    d.textRangeOpt(3, 8) === Some("lo wo")
  }

  @Test
  def textRangeOpt_returns_none_when_start_lower_than_zero() = {
    val d = document("hello world")
    d.textRangeOpt(-1, 8) === None
  }

  @Test
  def textRangeOpt_returns_none_when_end_lower_than_start() = {
    val d = document("hello world")
    d.textRangeOpt(5, 4) === None
  }

  @Test
  def textRangeOpt_returns_none_when_end_greater_than_length() = {
    val d = document("hello world")
    d.textRangeOpt(5, 20) === None
  }

  @Test
  def lines_returns_all_lines() = {
    val d = document("""|a
                        |bc
                        |def
                        |
                        |gh""".stripMargin)
    d.lines === Seq(
        d.Range(0,1),
        d.Range(2,4),
        d.Range(5,8),
        d.Range(9,9),
        d.Range(10,12))
  }

  @Test
  def lineCount_returns_the_number_of_lines() = {
    val d = document("""|a
                        |bc
                        |def
                        |
                        |gh""".stripMargin)
    d.lineCount === 5
  }

  @Test
  def trim_trims_whitespace() =
    " \t  hello \t  " becomes "hello" after (_.lineInformation(0).trim.text)

  @Test
  def trim_trims_nothing_when_there_is_no_whitespace() =
    "hello" becomes "hello" after (_.lineInformation(0).trim.text)

  @Test
  def trimLeft_trims_left_whitespace() =
    " \t  hello \t  " becomes "hello \t  " after (_.lineInformation(0).trimLeft.text)

  @Test
  def trimLeft_trims_nothing_when_there_is_no_whitespace() =
    "hello \t  " becomes "hello \t  " after (_.lineInformation(0).trimLeft.text)

  @Test
  def trimRight_trims_right_whitespace() =
    " \t  hello \t  " becomes " \t  hello" after (_.lineInformation(0).trimRight.text)

  @Test
  def trimRight_trims_nothing_when_there_is_no_whitespace() =
    " \t  hello" becomes " \t  hello" after (_.lineInformation(0).trimRight.text)
}