package scala.tools.eclipse.ui

import org.eclipse.jface.text.Document
import org.junit.ComparisonFailure
import org.junit.Test

object IndentGuideGeneratorTest {

  case class Range(startLine: Int, endLine: Int)

  val FullRange = Range(-1, -1)

  implicit class ToTest(input: String) extends AnyRef {
    def becomes(expected: String): (String, String) = {
      def clean(s: String) = {
        val r = s.stripMargin('|').filter(_ != '$')
        if (r.charAt(0) == '\n') r.tail else r
      }
      (clean(input), clean(expected))
    }
  }

  implicit class OrNotToTest(input: (String, String)) extends AnyRef with IndentGuideGenerator {
    private val doc = new Document(input._1)

    override def textOfLine(line: Int): String = {
      val r = doc.getLineInformation(line)
      doc.get(r.getOffset(), r.getLength())
    }
    override def lineCount = doc.getNumberOfLines()
    override def indentWidth = 2

    def in(range: Range): Unit = {
      val guides =
        if (range eq FullRange) guidesOfRange(0, doc.getNumberOfLines() - 1)
        else guidesOfRange(range.startLine, range.endLine)

      guides foreach {
        case Guide(line, column) =>
          val r = doc.getLineInformation(line)
          val len = r.getLength()

          if (column > len)
            doc.replace(r.getOffset() + len, 0, " " * (column - len) + "|")
          else
            doc.replace(r.getOffset() + column, 1, "|")
      }

      val actual = doc.get()
      val expected = input._2

      if (actual != expected)
        throw new ComparisonFailure("", expected, actual)
    }
  }
}

class IndentGuideGeneratorTest {
  import IndentGuideGeneratorTest._

  @Test
  def indentWithoutBlankLines(): Unit = """
    |class X {
    |  def f = {
    |    val x = {
    |      0
    |    }
    |    x
    |  }
    |}""" becomes """
    |class X {
    |  def f = {
    |  | val x = {
    |  | | 0
    |  | }
    |  | x
    |  }
    |}""" in FullRange

  @Test
  def indentOfMultiLineComment(): Unit = """
    |object X {
    |  /*
    |   *
    |   */
    |  def f = 0
    |}""" becomes """
    |object X {
    |  /*
    |  |*
    |  |*/
    |  def f = 0
    |}""" in FullRange

  @Test
  def noIndentOfBlankLine(): Unit = """
    |class X {
    |
    |}""" becomes """
    |class X {
    |
    |}""" in FullRange

  @Test
  def noIndentOfBlankLineWithWhitespace(): Unit = """
    |class X {
    |      $
    |}""" becomes """
    |class X {
    |      $
    |}""" in FullRange

  @Test
  def indentBlankLinesInsideIndentedBlock(): Unit = """
    |class X {
    |
    |  def f = {
    |
    |    $
    |    val x = {
    |
    |      def g = 0
    |
    |      0
    |    }
    |
    |    x
    |  }
    |
    |}""" becomes """
    |class X {
    |
    |  def f = {
    |  |
    |  | $
    |  | val x = {
    |  | |
    |  | | def g = 0
    |  | |
    |  | | 0
    |  | }
    |  |
    |  | x
    |  }
    |
    |}""" in FullRange

  @Test
  def indentInEmptyBlockClosedByBracket(): Unit = """
    |object X {
    |  def f[
    |    A
    |  ] = 0
    |}""" becomes """
    |object X {
    |  def f[
    |  | A
    |  ] = 0
    |}""" in FullRange

  @Test
  def indentInEmptyBlockClosedByBrace(): Unit = """
    |object X {
    |  def f = {
    |
    |  }
    |}""" becomes """
    |object X {
    |  def f = {
    |  |
    |  }
    |}""" in FullRange

  @Test
  def indentInEmptyBlockClosedByParenthesis(): Unit = """
    |object X {
    |  def f = (
    |
    |  )
    |}""" becomes """
    |object X {
    |  def f = (
    |  |
    |  )
    |}""" in FullRange

  @Test
  def indentInEmptyInnerBlock(): Unit = """
    |object X {
    |  def f = {
    |    def g = {
    |
    |    }
    |  }
    |}""" becomes """
    |object X {
    |  def f = {
    |  | def g = {
    |  | |
    |  | }
    |  }
    |}""" in FullRange

  @Test
  def noIndentBetweenBlocks(): Unit = """
    |object X {
    |  def f = 0
    |
    |  def g = 0
    |}""" becomes """
    |object X {
    |  def f = 0
    |
    |  def g = 0
    |}""" in FullRange

  @Test
  def indentInInnerBlockWithBlankLine(): Unit = """
    |object X {
    |  def f = {
    |    def g = {
    |      0
    |
    |
    |    }
    |    g
    |  }
    |}""" becomes """
    |object X {
    |  def f = {
    |  | def g = {
    |  | | 0
    |  | |
    |  | |
    |  | }
    |  | g
    |  }
    |}""" in FullRange

  @Test
  def noIndentAfterBlockThatIsNotSurroundedByBraces(): Unit = """
    |class X {
    |  def f(i: Int) =
    |    if (i > 0)
    |      i
    |
    |
    |    else
    |      0
    |
    |  h(0)
    |}""" becomes """
    |class X {
    |  def f(i: Int) =
    |  | if (i > 0)
    |  | | i
    |  |
    |  |
    |  | else
    |  | | 0
    |
    |  h(0)
    |}""" in FullRange

  @Test
  def noIndentAtClasslevel(): Unit = """
    |class X {
    |def f(i: Int) =
    |  if (i > 0)
    |    i
    |
    |
    |  else
    |    0
    |
    |h(0)
    |}""" becomes """
    |class X {
    |def f(i: Int) =
    |  if (i > 0)
    |  | i
    |
    |
    |  else
    |  | 0
    |
    |h(0)
    |}""" in FullRange

  @Test
  def indentInFirstLine(): Unit = """
    |      class X
    |}""" becomes """
    |  | | class X
    |}""" in FullRange

  @Test
  def blankLineInFirstOrLastLine(): Unit = """
    |
    |class X
    |
    |""" becomes """
    |
    |class X
    |
    |""" in FullRange

}