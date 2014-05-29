package org.scalaide.ui.internal.editor.autoedits

import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.preferences.EditorPreferencePage
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextUtilities
import org.scalaide.util.internal.ScalaWordFinder
import org.eclipse.jface.text.IRegion

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
    try {
      if (TextUtilities.endsWith(doc.getLegalLineDelimiters(), cmd.text) != -1) {
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
            buf append (cmd.text+indent)
            buf append (if (isDocStart) " */" else "*/")
            cmd.shiftsCaret = false
          }
        }
        cmd.text = buf.toString
      }
      else handleAutoLineBreak(doc, cmd)
    } catch {
      case e: Exception =>
        // don't break typing under any circumstances
        eclipseLog.warn("Error in scaladoc autoedit", e)
    }
  }

  private def handleAutoLineBreak(doc: IDocument, cmd: DocumentCommand): Unit = {
    import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants._

    def trimRightLen(str: String) =
      str.reverse.takeWhile(Character.isWhitespace).length()

    /**
     * Returns the start of the word at the given offset.
     * Delimiter sign is whitespace.
     */
    def wordStart(off: Int) = {
      def valid(i: Int) = !Character.isWhitespace(doc.getChar(i))

      var start = off-1
      while (start >= 0 && valid(start))
        start -= 1

      start+1
    }

    def doAutoBreak(line: IRegion) = {
      val systemLineSeparator = TextUtilities.getDefaultLineDelimiter(doc)
      val endOfIndent = findEndOfWhiteSpace(doc, line.getOffset(), cmd.offset)
      val innerIndentNeeded = doc.getChar(endOfIndent) == '*'
      val isCommentStart = doc.getChar(endOfIndent) == '/'

      val alignment =
        if (innerIndentNeeded) 1
        else if (doc.getChar(endOfIndent+3) != '*') 2
        else 3

      val textStart =
        if (innerIndentNeeded || isCommentStart)
          findEndOfWhiteSpace(doc, endOfIndent+alignment, cmd.offset)
        else
          endOfIndent

      val wordOff = wordStart(cmd.offset)
      val canSplitText = wordOff != textStart

      if (canSplitText) {
        val indent = doc.get(line.getOffset(), endOfIndent-line.getOffset())
        val word = doc.get(wordOff, cmd.offset-wordOff)

        val commentIndent =
          if (isCommentStart)
            " *" + doc.get(endOfIndent+alignment, textStart-endOfIndent-alignment)
          else
            doc.get(endOfIndent, textStart-endOfIndent)

        val newLine = Seq(systemLineSeparator, indent, commentIndent, word, cmd.text)

        val wsLen = trimRightLen(doc.get(textStart, wordOff-textStart))

        cmd.text = newLine.mkString
        cmd.length = cmd.offset-wordOff+wsLen
        cmd.offset = wordOff-wsLen
      }
    }

    val enableAutoBreaking = prefStore.getBoolean(
        EditorPreferencePage.P_ENABLE_AUTO_BREAKING_COMMENTS)

    if (enableAutoBreaking && cmd.text.nonEmpty) {
      val marginColumn = prefStore.getInt(EDITOR_PRINT_MARGIN_COLUMN)
      val line = doc.getLineInformationOfOffset(cmd.offset)
      val exceedMarginColumn = line.getLength() + cmd.text.length > marginColumn
      val singleSpace = cmd.text == " " && doc.getChar(cmd.offset-1) != ' '

      if (exceedMarginColumn && !singleSpace)
        doAutoBreak(line)
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
