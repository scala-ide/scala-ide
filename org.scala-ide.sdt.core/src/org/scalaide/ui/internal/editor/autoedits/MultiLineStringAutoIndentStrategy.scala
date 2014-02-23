package org.scalaide.ui.internal.editor.autoedits

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextUtilities
import org.scalaide.core.internal.formatter.FormatterPreferences._
import org.scalaide.ui.internal.preferences.EditorPreferencePage

import scalariform.formatter.preferences.IndentSpaces

class MultiLineStringAutoIndentStrategy(partitioning: String, prefStore: IPreferenceStore) extends DefaultIndentLineAutoEditStrategy {

  override def customizeDocumentCommand(doc: IDocument, cmd: DocumentCommand): Unit = {

    val isAutoIndentEnabled = prefStore.getBoolean(
      EditorPreferencePage.P_ENABLE_AUTO_INDENT_MULTI_LINE_STRING)
    val isStripMarginEnabled = prefStore.getBoolean(
      EditorPreferencePage.P_ENABLE_AUTO_STRIP_MARGIN_IN_MULTI_LINE_STRING)
    val tabSize = prefStore.getInt(IndentSpaces.eclipseKey)

    def indentOfLine(line: Int) = {
      val region = doc.getLineInformation(line)
      val begin = region.getOffset()
      val endOfWhitespace = findEndOfWhiteSpace(doc, begin, begin + region.getLength())
      doc.get(begin, endOfWhitespace - begin)
    }

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
        val indent = indentOfLine(line)
        cmd.text = s"\n$indent$additionalIndent"
      }

      def handleFirstLine = {
        def containsFirstLineStripMargin =
          doc.getChar(partition.getOffset() + 3) == '|'

        def handleFirstStripMarginLine = {
          val r = doc.getLineInformationOfOffset(cmd.offset)
          val lineIndent = indentOfLine(line)
          val indentCountToBar = partition.getOffset() - r.getOffset() - lineIndent.length + 3

          val innerIndent = {
            val barOffset = partition.getOffset() + 4
            val wsEnd = findEndOfWhiteSpace(doc, barOffset, r.getOffset() + r.getLength())
            doc.get(barOffset, wsEnd - barOffset)
          }

          val indent = s"\n$lineIndent${" " * indentCountToBar}|$innerIndent"
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

      def handleStripMarginLine = {
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
        val indent = s"\n$lineIndent${" " * indentCountToBar}|$innerIndent"

        cmd.text = indent
      }

      if (isFirstLine)
        handleFirstLine
      else if (isStripMarginEnabled && containsStripMargin)
        handleStripMarginLine
      else
        copyIndentOfPreviousLine("")
    }

    def indentOnTab() = {
      def textSize(indent: String) =
        indent.map(c => if (c == '\t') tabSize else 1).sum

      val line = doc.getLineOfOffset(cmd.offset)
      val prevLineIndent = indentOfLine(line - 1)
      val (curLineIndent, rest, restAfterCaret) = breakLine(doc, cmd.offset)

      val indent =
        if (prevLineIndent == curLineIndent) "  "
        else if (textSize(prevLineIndent) < textSize(curLineIndent)) "  "
        else if (rest != restAfterCaret) "  "
        else {
          if (curLineIndent.nonEmpty) {
            val r = doc.getLineInformationOfOffset(cmd.offset)
            cmd.offset = r.getOffset()
            cmd.length = curLineIndent.length()
          }
          prevLineIndent
        }
      cmd.text = indent
    }

    cmd.text match {
      case "\n" if isAutoIndentEnabled => autoIndentAfterNewLine()
      case "\t" if isAutoIndentEnabled => indentOnTab()
      case _ =>
    }
  }

  /** Return the whitespace prefix (indentation), the rest of the line
   *  for the given offset and also the rest of the line after the caret position.
   */
  private def breakLine(doc: IDocument, offset: Int): (String, String, String) = {
    // indent up to the previous line
    val lineInfo = doc.getLineInformationOfOffset(offset)
    val endOfWS = findEndOfWhiteSpace(doc, lineInfo.getOffset(), offset)
    val indent = doc.get(lineInfo.getOffset, endOfWS - lineInfo.getOffset)
    val rest = doc.get(endOfWS, lineInfo.getOffset + lineInfo.getLength() - endOfWS)
    val restAfterCaret = doc.get(offset, lineInfo.getOffset() - offset + lineInfo.getLength())
    (indent, rest, restAfterCaret)
  }

}