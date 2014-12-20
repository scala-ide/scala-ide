package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add
import org.scalaide.util.eclipse.RegionUtils._
import org.eclipse.jface.text.IRegion
import org.scalaide.core.text.Replace

object SurroundBlockSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[SurroundBlock],
  name = "Surround a block with curly braces",
  description = ExtensionSetting.formatDescription(
      """|In Scala, it happens very often that users write a definition that \
         |contains only a single expression. In these cases one can leave out \
         |curly braces:
         |
         |    def id(i: Int) =
         |      i
         |
         |Often, it happens that such a single expression needs to be expanded \
         |into multiple expressions, where braces need to be added. This auto \
         |edit helps in such cases and automatically adds the curly brace \
         |whenever the opening brace is inserted at the beginning of the block, \
         |which is in this case after the equal sign:
         |
         |    def id(i: Int) = ^
         |      i
         |
         |Here, ^ denotes the position of the cursor. Inserting `{` into the \
         |document results in:
         |
         |    def id(i: Int) = {^
         |      i
         |    }
         |
         |Note: The opening curly brace needs to be the last character of the \
         |line (excluding whitespace), otherwise no ending curly brace is added.
         |""".stripMargin)
)

trait SurroundBlock extends AutoEdit {

  def setting = SurroundBlockSetting

  def perform() = {
    rule(textChange) {
      case Add(start, "{") =>
        surroundLocation(start) map {
          case (pos, indentLen) =>
            val sep = System.getProperty("line.separator")
            Replace(start, pos, "{" + document.textRange(start, pos) + " "*indentLen + "}" + sep)
              .withCursorPos(start+1)
        }
    }
  }

  /**
   * Returns the position where the closing curly brace should be inserted and
   * the indentation of the line where the opening curly brace is inserted into
   * the document.
   *
   * In case no insertion position could be found, `None` is returned.
   */
  private def surroundLocation(offset: Int): Option[(Int, Int)] = {
    def indentLenOfLine(line: IRegion) = {
      val text = document.textRange(line.start, line.end)
      text.takeWhile(Character.isWhitespace).length()
    }
    val firstLine = document.lineInformationOfOffset(offset)
    val firstIndent = indentLenOfLine(firstLine)

    def find(offset: Int, isSecondLine: Boolean): Option[(Int, Int)] = {
      if (offset >= document.length)
        None
      else {
        val line = document.lineInformationOfOffset(offset)
        val indent = indentLenOfLine(line)

        if (indent <= firstIndent)
          if (isSecondLine)
            None
          else if (indent == firstIndent && document(line.trimLeft(document).start) == '}')
            None
          else
            Some(line.start -> firstIndent)
        else
          find(line.end+1, isSecondLine = false)
      }
    }

    if (offset == firstLine.trimRight(document).end+1)
      find(firstLine.end+1, isSecondLine = true)
    else
      None
  }
}
