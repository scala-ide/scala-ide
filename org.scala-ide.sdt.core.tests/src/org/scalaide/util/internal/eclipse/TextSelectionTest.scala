package org.scalaide.util.internal.eclipse

import org.eclipse.jface.text.Document
import org.eclipse.jface.text.TextSelection
import org.eclipse.text.edits.MultiTextEdit
import org.eclipse.text.edits.ReplaceEdit
import org.junit.ComparisonFailure
import org.junit.Test
import org.junit.Ignore

class TextSelectionTest {

  final implicit class OhILikeThisDslSoMuch(input: String) {
    def becomes(expectedOutput: String) = input -> expectedOutput
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

    val carets = input.count(_ == '^')
    require(carets == 1 || carets == 2, "No selection specified.")
    val selStart = input.indexOf('^')
    val selEnd = {
      val e = input.indexOf('^', selStart+1)
      if (e > selStart) e else selStart
    }

    def findBraces(pos: Int, m: Map[Int, Int]): Map[Int, Int] = {
      val open = input.indexOf("[", pos)

      if (open < 0)
        m
      else {
        val close = input.indexOf(']', open+1)
        val s = m.size*2

        // ^ = selStart/selEnd, [ = open, ] = close
        // case 1: ^  ^ [  ], ^ [  ]
        if (selStart < open && selEnd < open)
          findBraces(close+1, m + ((open-s-carets, close-s-carets-1)))
        // case 2: ^ [ ^ ]
        else if (selStart < open && selEnd < close)
          findBraces(close+1, m + ((open-s-1, close-s-3)))
        // case 3: ^ [  ] ^
        else if (selStart < open && selEnd > close)
          findBraces(close+1, m + ((open-s-1, close-s-2)))
        // case 4: [^  ^], [ ^ ]
        else if (selStart < close && selEnd < close)
          findBraces(close+1, m + ((open-s, close-s-carets-1)))
        // case 5: [ ^ ] ^
        else if (selStart < close && selEnd > close)
          findBraces(close+1, m + ((open-s, close-s-2)))
        // case 6: [  ] ^  ^, [  ] ^
        else// if (selStart > close && selEnd > close)
          findBraces(close+1, m + ((open-s, close-s-1)))
      }
    }

    def findSelection(source: String): TextSelection = {
      val open = source.indexOf('^')
      val close = source.indexOf('^', open+1)
      new TextSelection(open, if (close >= open) close-open-1 else 0)
    }

    require(input.count(_ == '[') == input.count(_ == ']'), "Invalid range area found.")
    val braces = findBraces(0, Map())
    val sourceWithoutBraces = input.replaceAll("\\[|\\]", "")

    require(braces.size == changes.size, "The number of changes need to be equal to the number of the regions.")
    val edit = new MultiTextEdit
    changes zip braces foreach {
      case (change, (start, end)) =>
        edit.addChild(new ReplaceEdit(start, end-start, change))
    }

    val sel = findSelection(sourceWithoutBraces)
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

  @Test
  def remove_after_cursor_position() = """|
    |class X {
    |  def f = 0^
    |  [def g = 0]
    |}
    |""".stripMargin becomes """|
    |class X {
    |  def f = 0^
    |  $
    |}
    |""".stripMargin after Seq("")

  @Test
  def multiple_remove_after_cursor_position() = """|
    |class X {
    |  def f = 0^
    |  [def g = 0]
    |}
    |[class S]
    |""".stripMargin becomes """|
    |class X {
    |  def f = 0^
    |  $
    |}
    |
    |""".stripMargin after Seq("", "")

  @Test
  def add_after_cursor_position() = """|
    |class X {
    |  def f = 0^
    |  []
    |}
    |""".stripMargin becomes """|
    |class X {
    |  def f = 0^
    |  def g = 0
    |}
    |""".stripMargin after Seq("def g = 0")

  @Test
  def multiple_add_after_cursor_position() = """|
    |class X {
    |  def f = 0^
    |  []
    |}
    |[]
    |""".stripMargin becomes """|
    |class X {
    |  def f = 0^
    |  def g = 0
    |}
    |class S
    |""".stripMargin after Seq("def g = 0", "class S")

  @Test
  def remove_before_and_after_cursor_position() = """|
    |class X {
    |  [def g = 0]
    |  def f = 0^
    |  [def x = 0]
    |}
    |""".stripMargin becomes """|
    |class X {
    |  $
    |  def f = 0^
    |  $
    |}
    |""".stripMargin after Seq("", "")

  @Test
  def multiple_remove_before_and_after_cursor_position() = """|
    |[class S]
    |class X {
    |  [def g = 0]
    |  def f = 0^
    |  [def x = 0]
    |}
    |[class M]
    |""".stripMargin becomes """|
    |
    |class X {
    |  $
    |  def f = 0^
    |  $
    |}
    |
    |""".stripMargin after Seq("", "", "", "")

  @Test
  def add_before_and_after_cursor_position() = """|
    |class X {
    |  []
    |  def f = 0^
    |  []
    |}
    |""".stripMargin becomes """|
    |class X {
    |  def g = 0
    |  def f = 0^
    |  def x = 0
    |}
    |""".stripMargin after Seq("def g = 0", "def x = 0")

  @Test
  def multiple_add_before_and_after_cursor_position() = """|
    |[]
    |class X {
    |  []
    |  def f = 0^
    |  []
    |}
    |[]
    |""".stripMargin becomes """|
    |class S
    |class X {
    |  def g = 0
    |  def f = 0^
    |  def x = 0
    |}
    |class M
    |""".stripMargin after Seq("class S", "def g = 0", "def x = 0", "class M")

  @Test
  def remove_and_add_before_and_after_cursor_position() = """|
    |[class S]
    |class X {
    |  []
    |  def f = 0^
    |  []
    |}
    |[class M]
    |""".stripMargin becomes """|
    |
    |class X {
    |  def g = 0
    |  def f = 0^
    |  def x = 0
    |}
    |
    |""".stripMargin after Seq("", "def g = 0", "def x = 0", "")

  @Test
  def remove_after_cursor_with_cursor_at_beginning_of_range() = """|
    |class X {
    |  def f = 0^[  ]
    |  [def g = 0]
    |}
    |""".stripMargin becomes """|
    |class X {
    |  def f = 0^
    |  $
    |}
    |""".stripMargin after Seq("", "")

  @Test
  def remove_after_cursor_with_cursor_at_end_of_range() = """|
    |class X {
    |  def f = 0[  ]^
    |  [def g = 0]
    |}
    |""".stripMargin becomes """|
    |class X {
    |  def f = 0^
    |  $
    |}
    |""".stripMargin after Seq("", "")

  @Test
  def remove_before_and_after_cursor_with_cursor_at_beginning_of_range() = """|
    |class X {
    |  [def g = 0]
    |  def f = 0^[  ]
    |}
    |""".stripMargin becomes """|
    |class X {
    |  $
    |  def f = 0^
    |}
    |""".stripMargin after Seq("", "")

  @Test
  def remove_before_and_after_with_cursor_at_end_of_range() = """|
    |class X {
    |  [def g = 0]
    |  def f = 0[  ]^
    |}
    |""".stripMargin becomes """|
    |class X {
    |  $
    |  def f = 0^
    |}
    |""".stripMargin after Seq("", "")

  @Test
  def add_after_cursor_with_cursor_at_beginning_of_range() = """|
    |class X {
    |  def f = 0^[]
    |  []
    |}
    |""".stripMargin becomes """|
    |class X {
    |  def f = 0+1^
    |  def g = 0
    |}
    |""".stripMargin after Seq("+1", "def g = 0")

  @Test
  def add_before_and_after_cursor_with_cursor_at_beginning_of_range() = """|
    |class X {
    |  []
    |  def f = 0^[]
    |}
    |""".stripMargin becomes """|
    |class X {
    |  def g = 0
    |  def f = 0+1^
    |}
    |""".stripMargin after Seq("def g = 0", "+1")

  @Test
  def remove_after_cursor_with_cursor_inside_of_range() = """|
    |class X {
    |  def f = 0[ ^ ]
    |  [def g = 0]
    |}
    |""".stripMargin becomes """|
    |class X {
    |  def f = 0^
    |  $
    |}
    |""".stripMargin after Seq("", "")

  @Test
  def remove_before_cursor_with_cursor_inside_of_range() = """|
    |class X {
    |  [def g = 0]
    |  def f = 0[ ^ ]
    |}
    |""".stripMargin becomes """|
    |class X {
    |  $
    |  def f = 0^
    |}
    |""".stripMargin after Seq("", "")

  // tests for selections

  @Test
  def remove_with_selection_case_1() = """|
    |class X {
    |  ^def g = 0^
    |  [def f = 0]
    |}
    |""".stripMargin becomes """|
    |class X {
    |  ^def g = 0^
    |  $
    |}
    |""".stripMargin after Seq("")

  @Test
  def remove_with_selection_case_2() = """|
    |^class X {
    |  [def g^ = 0]
    |  def f = 0
    |}
    |""".stripMargin becomes """|
    |^class X {
    |  $^
    |  def f = 0
    |}
    |""".stripMargin after Seq("")

  @Test
  def remove_with_selection_case_3() = """|
    |class M
    |^class X {
    |  [def g = 0]
    |  def f = 0
    |}^
    |class S
    |""".stripMargin becomes """|
    |class M
    |^class X {
    |  $
    |  def f = 0
    |}^
    |class S
    |""".stripMargin after Seq("")

  @Test
  def remove_with_selection_case_4() = """|
    |class X {
    |  [def ^g =^ 0]
    |  def f = 0
    |}
    |""".stripMargin becomes """|
    |class X {
    |  $^
    |  def f = 0
    |}
    |""".stripMargin after Seq("")

  @Test
  def remove_with_selection_case_5() = """|
    |class X {
    |  [def ^g = 0]
    |  def f = 0^
    |}
    |""".stripMargin becomes """|
    |class X {
    |  $^
    |  def f = 0^
    |}
    |""".stripMargin after Seq("")

  @Test
  def remove_with_selection_case_6() = """|
    |class X {
    |  [def g = 0]
    |  ^def f = 0^
    |}
    |""".stripMargin becomes """|
    |class X {
    |  $
    |  ^def f = 0^
    |}
    |""".stripMargin after Seq("")

  @Test
  def add_with_selection_case_1() = """|
    |class X {
    |  ^def g = 0^
    |  []
    |}
    |""".stripMargin becomes """|
    |class X {
    |  ^def g = 0^
    |  def f = 0
    |}
    |""".stripMargin after Seq("def f = 0")

  @Test
  def add_with_selection_case_3() = """|
    |class M
    |^class X {
    |  []
    |  def f = 0
    |}^
    |class S
    |""".stripMargin becomes """|
    |class M
    |^class X {
    |  def g = 0
    |  def f = 0
    |}^
    |class S
    |""".stripMargin after Seq("def g = 0")

  @Test
  def add_with_selection_case_6() = """|
    |class X {
    |  []
    |  ^def f = 0^
    |}
    |""".stripMargin becomes """|
    |class X {
    |  def g = 0
    |  ^def f = 0^
    |}
    |""".stripMargin after Seq("def g = 0")
}
