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

    def autoIndentAfterNewLine() = {
      val partition = TextUtilities.getPartition(doc, partitioning, cmd.offset, true)
      val partitionEnd = partition.getOffset() + partition.getLength()

      val line = doc.getLineOfOffset(cmd.offset)
      val isFirstLine = line == doc.getLineOfOffset(partition.getOffset())

      def containsStripMargin = {
        val len = ".stripMargin".length()
        if (partitionEnd + len >= doc.getLength()) false
        else doc.get(partitionEnd, len) matches "[ .]stripMargin"
      }

      def copyIndentOfPreviousLine(additionalIndent: String) = {
        val indent = indentOfLine(doc, line)
        cmd.text = s"${cmd.text}$indent$additionalIndent"
      }

      def handleFirstLine() = {
        def containsFirstLineStripMargin =
          doc.getChar(partition.getOffset() + 3) == '|'

        def handleFirstStripMarginLine() = {
          val r = doc.getLineInformationOfOffset(cmd.offset)
          val lineIndent = indentOfLine(doc, line)
          val indentCountToBar = partition.getOffset() - r.getOffset() - lineIndent.length + 3

          val innerIndent = {
            val barOffset = partition.getOffset() + 4
            val wsEnd = findEndOfWhiteSpace(doc, barOffset, r.getOffset() + r.getLength())
            doc.get(barOffset, wsEnd - barOffset)
          }

          val indent = s"${cmd.text}$lineIndent${" " * indentCountToBar}|$innerIndent"
          val isMultiLineStringClosed = partition.getOffset() + partition.getLength() != doc.getLength()

          if (isMultiLineStringClosed) {
            if (!containsStripMargin)
              cmd.addCommand(partitionEnd, 0, ".stripMargin", null)
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
        val indentCountToBar = partition.getOffset() - r.getOffset() - lineIndent.length + 3
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
      else if (isStripMarginEnabled && containsStripMargin)
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