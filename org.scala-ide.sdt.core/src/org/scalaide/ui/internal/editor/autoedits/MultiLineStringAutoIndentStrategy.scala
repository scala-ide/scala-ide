package org.scalaide.ui.internal.editor.autoedits

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextUtilities
import org.scalaide.ui.internal.preferences.EditorPreferencePage

class MultiLineStringAutoIndentStrategy(partitioning: String, prefStore: IPreferenceStore) extends AutoIndentStrategy(prefStore) {

  override def customizeDocumentCommand(doc: IDocument, cmd: DocumentCommand): Unit = {
    val isAutoIndentEnabled = prefStore.getBoolean(
      EditorPreferencePage.P_ENABLE_AUTO_INDENT_MULTI_LINE_STRING)
    val isStripMarginEnabled = prefStore.getBoolean(
      EditorPreferencePage.P_ENABLE_AUTO_STRIP_MARGIN_IN_MULTI_LINE_STRING)

    /** Finds the partition at `offset`. */
    def partitionAt(offset: Int, preferOpenPartitions: Boolean) =
      TextUtilities.getPartition(doc, partitioning, offset, preferOpenPartitions)

    /**
     * Finds the end of the string literal which is at `offset`. This function
     * is necessary because in case of a string interpolation we have to
     * traverse multiple partitions in order to get the end position.
     */
    def stringInterpolationEnd(offset: Int): Int =
      if (offset >= doc.getLength)
        doc.getLength
      else {
        val p = partitionAt(offset, preferOpenPartitions = false)
        val end = p.getOffset+p.getLength
        if (doc.get(end-3, 3) == "\"\"\"")
          end
        else
          stringInterpolationEnd(end)
      }

    /**
     * Finds the start of the string literal which is at `offset`. This function
     * is necessary because in case of a string interpolation we have to
     * traverse multiple partitions in order to get the start position.
     */
    def stringInterpolationStart(offset: Int): Int =
      if (offset <= 0)
        0
      else {
        val p = partitionAt(offset, preferOpenPartitions = false)
        if (p.getLength >= 3 && doc.get(p.getOffset, 3) == "\"\"\"")
          p.getOffset
        else
          stringInterpolationStart(p.getOffset-1)
      }

    def autoIndentAfterNewLine() = {
      def isTripleQuotes(offset: Int, len: Int) =
        len >= 3 && doc.get(offset, 3) == "\"\"\""

      val lineOfCursor = doc.getLineOfOffset(cmd.offset)
      val (start, end, isFirstLine) = {
        val p = partitionAt(cmd.offset, preferOpenPartitions = true)
        val pEnd = p.getOffset+p.getLength
        val start = stringInterpolationStart(p.getOffset)

        val isFirstLine =
          if (isTripleQuotes(p.getOffset, p.getLength))
            lineOfCursor == doc.getLineOfOffset(p.getOffset())
          else
            lineOfCursor == doc.getLineOfOffset(start)

        val end =
          if (doc.getChar(pEnd-1) == '$')
            stringInterpolationEnd(pEnd)
          else
            pEnd

        (start, end, isFirstLine)
      }

      def containsStripMargin(offset: Int) = {
        val len = ".stripMargin".length()
        if (offset + len >= doc.getLength()) false
        else doc.get(offset, len) matches "[ .]stripMargin"
      }

      def copyIndentOfPreviousLine(additionalIndent: String) = {
        val indent = indentOfLine(doc, lineOfCursor)
        cmd.text = s"${cmd.text}$indent$additionalIndent"
      }

      def handleFirstLine() = {
        def containsFirstLineStripMargin =
          doc.getChar(start + 3) == '|'

        def handleFirstStripMarginLine() = {
          val r = doc.getLineInformationOfOffset(cmd.offset)
          val lineIndent = indentOfLine(doc, lineOfCursor)

          val indentCountToBar =
            if (isTripleQuotes(start, end-start))
              start - r.getOffset() - lineIndent.length + 3
            else
              0

          val innerIndent = {
            val barOffset = start + 4
            val wsEnd = findEndOfWhiteSpace(doc, barOffset, r.getOffset() + r.getLength())
            doc.get(barOffset, wsEnd - barOffset)
          }

          val indent = s"${cmd.text}$lineIndent${" " * indentCountToBar}|$innerIndent"
          val isMultiLineStringClosed = end != doc.getLength()

          if (isMultiLineStringClosed) {
            if (!containsStripMargin(end))
              cmd.addCommand(end, 0, ".stripMargin", null)
            cmd.caretOffset = cmd.offset + indent.length()
            cmd.shiftsCaret = false
          }

          cmd.text = indent
        }

        if (isStripMarginEnabled && containsFirstLineStripMargin)
          handleFirstStripMarginLine
        else
          copyIndentOfPreviousLine("  ")
      }

      def handleStripMarginLine() = {
        val r = doc.getLineInformationOfOffset(cmd.offset)
        val (lineIndent, rest, _) = breakLine(doc, cmd.offset)

        val indentCountToBar =
          if (isTripleQuotes(start, end-start))
            start - r.getOffset() - lineIndent.length + 3
          else
            0

        val innerIndent =
          if (!rest.startsWith("|"))
            ""
          else {
            val barOffset = r.getOffset() + lineIndent.length() + 1
            val wsEnd = findEndOfWhiteSpace(doc, barOffset, r.getOffset() + r.getLength())
            doc.get(barOffset, wsEnd - barOffset)
          }
        val indent = s"${cmd.text}$lineIndent${" " * indentCountToBar}|$innerIndent"

        cmd.text = indent
      }

      if (isFirstLine)
        handleFirstLine
      else if (isStripMarginEnabled && containsStripMargin(end))
        handleStripMarginLine
      else
        copyIndentOfPreviousLine("")
    }

    def isNewlineSign = doc.getLegalLineDelimiters().exists(_ == cmd.text)

    cmd.text match {
      case _ if isAutoIndentEnabled && isNewlineSign => autoIndentAfterNewLine()
      case "\t" if isAutoIndentEnabled               => indentOnTab(doc, cmd, indentWithTabs, tabSize)
      case "\t"                                      => cmd.text = oneIndent(indentWithTabs, tabSize)
      case _                                         =>
    }
  }
}
