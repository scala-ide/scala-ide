package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add
import org.scalaide.core.text.Change

object SmartSemicolonInsertionSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[SmartSemicolonInsertion],
  name = "Smart semicolon insertion",
  description =
    """|Automatically inserts a typed semicolon at the correct position. This
       | will move the semicolon and the cursor to the end of the line if no
       | other semicolon already exists. If the automatic move is unwanted,
       | one can press the backspace key to move the semicolon back to the
       | previous location of the cursor.""".stripMargin.replaceAll("\n", "")
)

trait SmartSemicolonInsertion extends AutoEdit {

  override def setting: AutoEditSetting = SmartSemicolonInsertionSetting

  override def perform: Option[Change] = {
    rule(textChange) {
      case Add(start, ";") =>
        hanldleSmartInsert(';')(computeSemicolonInsertPosition)
    }
  }

  /**
   * Computes the absolute insertion position of a semicolon in a given `line`
   */
  def computeSemicolonInsertPosition(line: document.Range): Option[Int] = {
    val cursorRelPos = textChange.start - line.start

    val i = line.text.indexOf("for")
    val noMoveNecessary = i >= 0 && i < cursorRelPos

    if (noMoveNecessary)
      None
    else
      Some(line.trimRight.end)
  }

  /**
   * Expects a function `f` that computes the insert position of a char `c` to a
   * given line. `f` needs to return `None` if no more accurate insert position
   * than the current cursor position is found.
   */
  def hanldleSmartInsert(c: Char)(f: document.Range => Option[Int]): Option[Change] = {
    val l = document.lineInformationOfOffset(textChange.start)
    def alreadyPresent(off: Int) = document(off) == c

    f(l) filter (p => !alreadyPresent(p-1)) map { insertPos =>
      val add = Add(insertPos, ";") withCursorPos insertPos+1
      add.copy(smartBackspaceEnabled = true)
    }
  }
}
