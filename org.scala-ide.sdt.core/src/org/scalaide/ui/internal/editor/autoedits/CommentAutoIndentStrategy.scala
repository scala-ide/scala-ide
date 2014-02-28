package org.scalaide.ui.internal.editor.autoedits

import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.preferences.EditorPreferencePage
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextUtilities

/** An auto-edit strategy for Scaladoc and multiline comments that does the following:
 *
 *  - adds '*' and left-aligns them when pressing enter
 *  - automatically indentates to the start position of the text in the previous line of code
 *  - auto-closes an open Scaladoc or multiline comment and places the cursor in between
 *  - allows to enlarge a comment block without adding a star to the following line
 *    by pressing enter on an empty line
 */
class CommentAutoIndentStrategy(prefStore: IPreferenceStore, partitioning: String) extends AutoIndentStrategy(prefStore) with HasLogger {

  override def customizeDocumentCommand(doc: IDocument, cmd: DocumentCommand) {
    if (cmd.offset == -1 || doc.getLength() == 0) return // don't spend time on invalid docs

    try {
      if (cmd.length == 0 && cmd.text != null && TextUtilities.endsWith(doc.getLegalLineDelimiters(), cmd.text) != -1) {
        val shouldClose = {
          val isAutoClosingEnabled = prefStore.getBoolean(
              EditorPreferencePage.P_ENABLE_AUTO_CLOSING_COMMENTS)
          isAutoClosingEnabled && shouldCloseDocComment(doc, cmd.offset)
        }

        val (indent, rest, restAfterCaret) = breakLine(doc, cmd.offset)
        val buf = new StringBuilder(cmd.text)
        buf.append(indent)

        lazy val shouldAddAsterisk = shouldClose || rest(0) == '/' || rest(0) == '*'

        if (!rest.isEmpty() && shouldAddAsterisk) {
          val isDocStart = rest(0) == '/'
          val docStarSize = if (isDocStart) 1 else 0
          val isScaladoc = rest.length > 2 && rest.charAt(2) == '*'

          /* Returns the white space indentation count */
          def commentTextIndentation(i: Int) = {
            val lineInfo = doc.getLineInformationOfOffset(cmd.offset)
            val signsBetweenSpacesAndCursor = cmd.offset - lineInfo.getOffset() - indent.length()
            rest.take(signsBetweenSpacesAndCursor).drop(i + docStarSize).takeWhile(_ == ' ').size
          }

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
            if (restAfterCaret.nonEmpty) {
              buf.append(restAfterCaret)
              cmd.addCommand(cmd.offset, restAfterCaret.length(), "", null)
            }

            // we want the caret before the closing comment
            cmd.caretOffset = cmd.offset + buf.length - restAfterCaret.length()
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

  /** Heuristics for when to close a Scaladoc. Returns `true` when the offset is
   *  inside a Scaladoc that runs to the end of the document or if the line
   *  containing the end of the Scaladoc section contains a quotation mark. This
   *  handles nested comments pretty well because it uses the Scala document
   *  partitioner.
   */
  private def shouldCloseDocComment(doc: IDocument, offset: Int): Boolean = {
    def isProbablyString = {
      val p = TextUtilities.getPartition(doc, partitioning, offset, true)
      val start = doc.getLineInformationOfOffset(p.getOffset()).getOffset()
      val end = p.getOffset() + p.getLength() - start

      val containsSingleQuote = doc.get(start, end).reverse.exists(_ == '"')
      scaladocPartitions(p.getType()) && containsSingleQuote
    }

    val partition = TextUtilities.getPartition(doc, partitioning, doc.getLength() - 1, true)
    scaladocPartitions(partition.getType()) || isProbablyString
  }

  private val scaladocPartitions = Set(IJavaPartitions.JAVA_DOC, IJavaPartitions.JAVA_MULTI_LINE_COMMENT)
}
