package scala.tools.eclipse.ui

import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextUtilities
import scala.tools.eclipse.lexical.ScalaPartitions
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.BadLocationException
import scala.tools.eclipse.logging.HasLogger

/** A Scaladoc auto-edit strategy that does the following:
 *
 *  - adds '*' and left-aligns them when pressing enter
 *  - auto-closes an open ScalaDoc and places the cursor in between
 */
class ScaladocAutoIndentStrategy(partitioning: String) extends DefaultIndentLineAutoEditStrategy with HasLogger {

  override def customizeDocumentCommand(doc: IDocument, cmd: DocumentCommand) {
    if (cmd.offset == -1 || doc.getLength() == 0) return // don't spend time on invalid docs

    try {
      if (cmd.length == 0 && cmd.text != null && TextUtilities.endsWith(doc.getLegalLineDelimiters(), cmd.text) != -1) {
        val shouldClose = shouldCloseDocComment(doc, cmd.offset)

        val (indent, rest) = breakLine(doc, cmd.offset)
        val buf = new StringBuilder(cmd.text)
        buf.append(indent)
        val docStart = rest.length > 0 && rest(0) == '/'

        // align the star under the other star
        if (docStart) buf.append(" * ") else buf.append("* ")

        if (shouldClose) {
          // we want the caret before the closing comment
          cmd.caretOffset = cmd.offset + buf.length
          buf append ("\n" + indent)
          buf append (if (docStart) " */" else "*/")
          cmd.shiftsCaret = false
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
