package org.scalaide.util.internal.eclipse

import org.eclipse.jface.text.Document
import org.eclipse.jface.text.TextSelection
import org.eclipse.text.edits.MultiTextEdit
import org.eclipse.text.edits.ReplaceEdit
import org.junit.ComparisonFailure
import org.junit.Test

class EditorUtilsTest {

  final implicit class OhILikeThisDslSoMuch(input: String) {
    def becomes(expectedOutput: String) = input → expectedOutput
  }
  final implicit class IWannaHaveMoreOfIt(testData: (String, String)) {
    def after(changes: Seq[String]) = test(testData._1, testData._2, changes)
  }

  /**
   * Test if the text selection is correct when `changes` are made to `input`,
   * which then result to `expectedOutput`.
   *
   * Features:
   * - Regions that should be added or removed need to be surrounded by []
   * - For each region in the test string a value need to exist in `changes`. In
   *   case a region needs to be removed, the value needs to be empty. In case
   *   a region should be added or replaced, the value needs to be non empty.
   * - The cursor position is determined by a ^. If a region should be selected,
   *   a second ^ needs to be placed. The region between the two ^ then
   *   determines the selection.
   * - If trailing whitespace needs to exist in the test string, a trailing $
   *   can be placed after the whitespace.
   */
  final def test(input: String, expectedOutput: String, changes: Seq[String]): Unit = {

    def findBraces(pos: Int, m: Map[Int, Int]): Map[Int, Int] = {
      val open = input.indexOf("[", pos)

      if (open < 0)
        m
      else {
        val close = input.indexOf(']', open+1)
        val s = (m.size)*2
        findBraces(close, m + ((open-s, close-s-1)))
      }
    }

    def findSelection(source: String): TextSelection = {
      val open = source.indexOf('^')
      val close = source.indexOf('^', open+1)
      new TextSelection(open, if (close >= open) close-open else 0)
    }

    require(input.count(_ == '[') == input.count(_ == ']'), "Invalid range area found.")
    val braces = findBraces(0, Map())
    val sourceWithoutBraces = input.replaceAll("\\[|\\]", "")

    require(((n: Int) ⇒ n == 1 || n == 2)(sourceWithoutBraces.count(_ == '^')), "No selection specified.")
    val sel = findSelection(sourceWithoutBraces)

    require(braces.size == changes.size, "The number of changes need to be equal to the number of the regions.")
    val edit = new MultiTextEdit
    changes zip braces foreach {
      case (change, (start, end)) ⇒
        edit.addChild(new ReplaceEdit(start, end-start, change))
    }

    val sourceWithoutCursor = sourceWithoutBraces.replaceAll("\\^", "")
    val doc = new Document(sourceWithoutCursor)
    val s = EditorUtils.applyMultiTextEdit(doc, sel, edit)

    doc.replace(s.getOffset(), 0, "^")
    if (s.getLength() > 0)
      doc.replace(s.getOffset()+s.getLength()+1, 0, "^")

    val expected = expectedOutput.replaceAll("\\$", "")
    val actual = doc.get()

    if (expected != actual) {
      throw new ComparisonFailure("", expected, actual)
    }
  }

  @Test
  def remove_before_cursor_position() = """|
    |class X {
    |  [def g = 0]
    |  def f = 0^
    |}
    |""".stripMargin becomes """|
    |class X {
    |  $
    |  def f = 0^
    |}
    |""".stripMargin after Seq("")

  @Test
  def multiple_remove_before_cursor_position() = """|
    |[class S]
    |class X {
    |  [def g = 0]
    |  def f = 0^
    |}
    |""".stripMargin becomes """|
    |
    |class X {
    |  $
    |  def f = 0^
    |}
    |""".stripMargin after Seq("", "")

  @Test
  def add_before_cursor_position() = """|
    |class X {
    |  []
    |  def f = 0^
    |}
    |""".stripMargin becomes """|
    |class X {
    |  def g = 0
    |  def f = 0^
    |}
    |""".stripMargin after Seq("def g = 0")

  @Test
  def multiple_add_before_cursor_position() = """|
    |[]
    |class X {
    |  []
    |  def f = 0^
    |}
    |""".stripMargin becomes """|
    |class S
    |class X {
    |  def g = 0
    |  def f = 0^
    |}
    |""".stripMargin after Seq("class S", "def g = 0")
}
