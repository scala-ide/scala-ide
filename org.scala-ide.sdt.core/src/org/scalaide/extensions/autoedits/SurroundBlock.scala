package org.scalaide.extensions
package autoedits

import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.scalaide.core.text.Add
import org.scalaide.core.text.Replace
import org.scalaide.util.eclipse.RegionUtils._

import scalariform.lexer._

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
         |""".stripMargin),
  partitions = Set(IDocument.DEFAULT_CONTENT_TYPE)
)

trait SurroundBlock extends AutoEdit {

  override def setting = SurroundBlockSetting

  override def perform() = {
    val elseLikeTokens = Set(Tokens.ELSE, Tokens.CATCH, Tokens.FINALLY)
    check(textChange) {
      case Add(start, "{") =>
        surroundLocation(start) map {
          case (pos, indentLen, token) =>
            val indent = " " * indentLen

            val change = if (elseLikeTokens(token.tokenType))
              Replace(start, pos + indentLen, s"{${document.textRange(start, pos)}$indent} ")
            else
              Replace(start, pos, s"{${document.textRange(start, pos)}$indent}${document.defaultLineDelimiter}")
            change.withCursorPos(start+1)
        }
    }
  }

  /**
   * Returns a triple with the position where the closing curly brace should be inserted,
   * the indentation of the line where the opening curly brace is inserted into the document
   * and the first token after the insertion point.
   *
   * In case no insertion position could be found, `None` is returned.
   */
  private def surroundLocation(offset: Int): Option[(Int, Int, Token)] = {
    def indentLenOfLine(line: IRegion) = {
      val text = document.textRange(line.start, line.end)
      text.takeWhile(Character.isWhitespace).length()
    }
    val firstLine = document.lineInformationOfOffset(offset)
    val firstIndent = indentLenOfLine(firstLine)
    val lexer = ScalaLexer.createRawLexer(document.textRange(offset, document.length-1), forgiveErrors = true)

    def loop(): Option[(Int, Token)] =
      if (!lexer.hasNext)
        None
      else {
        import Tokens._
        val t = lexer.next()

        if (t.tokenType == RBRACE
             || t.tokenType == VARID
             || COMMENTS.contains(t.tokenType)
             || (Tokens.KEYWORDS contains t.tokenType)) {
          val line = document.lineInformationOfOffset(t.offset+offset)
          val indent = indentLenOfLine(line)

          if (t.tokenType == Tokens.RBRACE && indent == firstIndent)
            None
          else if (indent <= firstIndent) {
            var prevLine = document.lineInformationOfOffset(line.start-1)

            while (prevLine.trim(document).length == 0)
              prevLine = document.lineInformationOfOffset(prevLine.start-1)

            if (prevLine.start == firstLine.start)
              None
            else
              Some((prevLine.end+1, t))
          }
          else
            loop()
        }
        else
          loop()
      }

    if (offset == firstLine.trimRight(document).end+1)
      loop() map { case (line, token) => (line, firstIndent, token) }
    else
      None
  }
}
