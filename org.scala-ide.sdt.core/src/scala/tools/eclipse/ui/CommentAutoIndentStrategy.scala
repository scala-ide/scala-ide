package scala.tools.eclipse.ui

import scala.tools.eclipse.logging.HasLogger

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.{DefaultIndentLineAutoEditStrategy, DocumentCommand, IDocument, TextUtilities}

/** An auto-edit strategy for Scaladoc and multiline comments that does the following:
 *
 *  - adds '*' and left-aligns them when pressing enter
 *  - automatically indentates to the start position of the text in the previous line of code
 *  - auto-closes an open Scaladoc or multiline comment and places the cursor in between
 *  - allows to enlarge a comment block without adding a star to the following line
 *    by pressing enter on an empty line
 */
class CommentAutoIndentStrategy(partitioning: String) extends DefaultIndentLineAutoEditStrategy with HasLogger {

  override def customizeDocumentCommand(doc: IDocument, cmd: DocumentCommand) {
    if (cmd.offset == -1 || doc.getLength() == 0) return // don't spend time on invalid docs

    try {
      if (cmd.length == 0 && cmd.text != null && TextUtilities.endsWith(doc.getLegalLineDelimiters(), cmd.text) != -1) {
        val shouldClose = shouldCloseDocComment(doc, cmd.offset)

        val (indent, rest) = breakLine(doc, cmd.offset)
        val buf = new StringBuilder(cmd.text)
        buf.append(indent)

        lazy val shouldAddAsterisk = shouldClose || rest(0) == '/' || rest(0) == '*'

        if (!rest.isEmpty() && shouldAddAsterisk) {
          val isDocStart = rest(0) == '/'
          val docStarSize = if (isDocStart) 1 else 0
          val isScaladoc = rest.length > 2 && rest.charAt(2) == '*'

          /* Returns the white space indentation count */
          def commentTextIndentation(i: Int) =
            rest.drop(i + docStarSize).takeWhile(c => c == ' ' || c == '\t').size

          val textIndent = {
            val indent =
              if (doc.getChar(cmd.offset - 1) == '/') 1
              else if (isScaladoc) commentTextIndentation(2) + 1
              else commentTextIndentation(1)

            if (indent == 0) 1 else indent
          }

          buf.append(if (isDocStart) " *" else "*")
          buf.append(" " * textIndent)

          if (shouldClose) {
            // we want the caret before the closing comment
            cmd.caretOffset = cmd.offset + buf.length
            buf append ("\n"+indent)
            buf append (if (isDocStart) " */" else "*/")
            cmd.shiftsCaret = false
          }
        }
        cmd.text = buf.toString
      }
    } catch {
      case e: Exception =>
        // don't break typing under any circumstances
        eclipseLog.warn("Error in scaladoc autoedit", e)
    }
  }

  /** Return the whitespace prefix (indentation) and the rest of the line
   *  for the given offset.
   */
  private def breakLine(doc: IDocument, offset: Int): (String, String) = {
    // indent up to the previous line
    val lineInfo = doc.getLineInformationOfOffset(offset)
    val endOfWS = findEndOfWhiteSpace(doc, lineInfo.getOffset(), offset)
    (doc.get(lineInfo.getOffset, endOfWS - lineInfo.getOffset), doc.get(endOfWS, lineInfo.getOffset + lineInfo.getLength() - endOfWS))
  }

  /** Heuristics for when to close a scaladoc. Returns `true` when the offset is inside a scaladoc
   *  that runs to the end of the document. This handles nested comments pretty well because
   *  it uses the Scala document partitioner.
   */
  private def shouldCloseDocComment(doc: IDocument, offset: Int): Boolean = {
    val partition = TextUtilities.getPartition(doc, partitioning, offset, true)
    val partitionEnd = partition.getOffset() + partition.getLength()
    (scaladocPartitions(partition.getType())
      && partitionEnd == doc.getLength())
  }

  private val scaladocPartitions = Set(IJavaPartitions.JAVA_DOC, IJavaPartitions.JAVA_MULTI_LINE_COMMENT)
}
