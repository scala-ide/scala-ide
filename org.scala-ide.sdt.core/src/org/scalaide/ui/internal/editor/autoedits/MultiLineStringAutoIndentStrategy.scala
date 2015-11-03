package org.scalaide.ui.internal.editor.autoedits

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextUtilities
import org.scalaide.core.internal.statistics.Features
import org.scalaide.ui.internal.preferences.EditorPreferencePage
import org.scalaide.util.eclipse.RegionUtils._

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
     * Checks if the region between `offset` and `offset+len` starts with three
     * quotation marks.
     */
    def isTripleQuotes(offset: Int, len: Int) =
      len >= 3 && doc.get(offset, 3) == "\"\"\""

    /**
     * Finds the end of the string literal which is at `offset`. This function
     * is necessary because in case of a string interpolation we have to
     * traverse multiple partitions in order to get the end position.
     */
    def stringInterpolationEnd(offset: Int): Int =
      if (offset >= doc.length)
        doc.length
      else {
        val p = partitionAt(offset, preferOpenPartitions = false)
        if (isTripleQuotes(p.end-3, p.length))
          p.end
        else
          stringInterpolationEnd(p.end)
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
        if (isTripleQuotes(p.start, p.length))
          p.start
        else
          stringInterpolationStart(p.start-1)
      }

    /**
     * Calculates the indentation and strip margin that should be inserted
     * after a newline.
     */
    def autoIndentAfterNewLine() = {
      Features.AutoIndentMultiLineStrings.incUsageCounter()
      val lineOfCursor = doc.getLineOfOffset(cmd.offset)
      val (start, end, isFirstLine) = {
        val p = partitionAt(cmd.offset, preferOpenPartitions = true)
        val start = stringInterpolationStart(p.start)

        val isFirstLine =
          if (isTripleQuotes(p.start, p.length))
            lineOfCursor == doc.getLineOfOffset(p.start)
          else
            lineOfCursor == doc.getLineOfOffset(start)

        val end =
          if (doc.getChar(p.end-1) == '$')
            stringInterpolationEnd(p.end)
          else
            p.end

        (start, end, isFirstLine)
      }

      def containsStripMargin(offset: Int) = {
        val len = ".stripMargin".length
        if (offset+len >= doc.length)
          false
        else
          doc.get(offset, len) matches "[ .]stripMargin"
      }

      def copyIndentOfPreviousLine(additionalIndent: String) = {
        val indent = indentOfLine(doc, lineOfCursor)
        cmd.text = s"${cmd.text}$indent$additionalIndent"
      }

      def mkIndent = {
        val r = doc.getLineInformationOfOffset(cmd.offset)
        val lineIndent =
          if (isFirstLine)
            indentOfLine(doc, lineOfCursor)
          else
            breakLine(doc, cmd.offset)._1
        val indentCountToBar = start - r.start - lineIndent.length + 3

        val innerIndent = {
          val barOffset =
            if (isFirstLine)
              start+4
            else
              r.start + lineIndent.length() + 1
          val wsEnd = findEndOfWhiteSpace(doc, barOffset, r.start + r.length)
          doc.get(barOffset, wsEnd - barOffset)
        }

        s"${cmd.text}$lineIndent${" " * indentCountToBar}|$innerIndent"
      }

      def handleFirstLine() = {
        def containsFirstLineStripMargin =
          doc.getChar(start + 3) == '|'

        def handleFirstStripMarginLine() = {
          Features.AutoAddStripMargin.incUsageCounter()
          val indent = mkIndent
          val isMultiLineStringClosed = end != doc.length

          if (isMultiLineStringClosed) {
            if (!containsStripMargin(end))
              cmd.addCommand(end, 0, ".stripMargin", null)
            cmd.caretOffset = cmd.offset + indent.length
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
        Features.AutoAddStripMargin.incUsageCounter()
        cmd.text = mkIndent
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
